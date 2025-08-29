package August.day25.Multi;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    static final private int PORT = 5000;  // 포트번호 설정
    // 다중 접속을 ThreadPool로 관리
    static final private ExecutorService POOL = Executors.newCachedThreadPool();
    // 사용자 번호 자동 증가 (옵션)
    static final private AtomicInteger CLIENT_SEQ = new AtomicInteger(1);

    // 사용자 이름을 저장할 Set (중복 방지에 유리)
    private static final Set<String> NAMES = ConcurrentHashMap.newKeySet();
    // 클라이언트를 관리할 List
    private static final List<August.day25.Multi.Server.ClientHandler> CLIENTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("[서버] 서버 시작");
        // 서버 소켓 생성
        ServerSocket serverSocket = new ServerSocket(PORT);

        // Ctrl + C 입력시 종료 (터미널에서 동작 보장, IDE는 Stop 버튼 권장)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[서버] 서버 종료...");
            try {
                POOL.shutdownNow();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace(); // 훅에서 예외 재던지지 말고 로그만
            }
        }));

        while (true) {
            // 클라이언트 연결 기다리기
            Socket clientSocket = serverSocket.accept();
            System.out.println("[서버] 클라이언트와 연결되었습니다.");
            int seq = CLIENT_SEQ.getAndIncrement();

            // 스레드 풀에 클라이언트 작업 제출
            POOL.submit(new August.day25.Multi.Server.ClientHandler(clientSocket, seq));
        }
    }

    // 전체에게 전송
    static void broadcast(String message) {
        for (August.day25.Multi.Server.ClientHandler ch : CLIENTS) ch.send(message);
    }

    // 보낸 사람 제외 전송
    static void broadcastExcept(String message, August.day25.Multi.Server.ClientHandler except) {
        for (August.day25.Multi.Server.ClientHandler ch : CLIENTS) if (ch != except) ch.send(message);
    }

    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int sequence;
        private BufferedReader in;
        private PrintWriter out;
        private String name;
        private final Object writeLock = new Object(); // per-client 출력 직렬화용 락

        public ClientHandler(Socket socket, int sequence) {
            this.socket = socket;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            try {
                // 클라이언트 Request 받는 InputStreamReader
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                // 클라이언트에게 Response 를 하기 위한 OutputStream (autoFlush=true)
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 닉네임 수신 및 등록 (재입력 루프)
                out.println("닉네임을 입력하세요:");
                while (true) {

                    String candidate = in.readLine();
                    if (candidate == null) {
                        // 접속 도중 끊김
                        return;
                    }
                    candidate = candidate.trim();
                    if (candidate.isEmpty()) {
                        out.println("빈 닉네임은 사용할 수 없습니다. 다시 입력하세요:");
                        continue;
                    }
                    if ("/quit".equalsIgnoreCase(candidate)) {
                        out.println("연결을 종료합니다.");
                        return;
                    }

                    // Set.add()는 중복이면 false
                    if (!NAMES.add(candidate)) {
                        out.println("이미 사용 중인 닉네임입니다. 다른 이름으로 입력하세요:");
                    } else {
                        this.name = candidate;
                        break; // 닉네임 확정
                    }
                }

                CLIENTS.add(this);

                out.println(name + "님 반갑습니다! (/quit 종료)");
                August.day25.Multi.Server.broadcast("[서버] " + name + "님 입장. 현재 " + NAMES.size() + "명");

                // 명령 처리 루프
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("[서버] 수신: " + clientMessage);

                    switch (clientMessage) {
                        case "/quit" -> {
                            out.println(name + "님 안녕히 가세요.");
                            return; // finally에서 정리
                        }
                        case "/who" -> {
                            out.println("현재 접속 인원: " + NAMES.size() + "명: " + String.join(", ", NAMES));
                        }
                        case "/text" -> {
                            out.println("메시지를 입력하세요:");
                            String msg = in.readLine();
                            if (msg != null && !msg.isBlank()) {
                                // 보낸 사람 제외 전체 방송
                                August.day25.Multi.Server.broadcastExcept("[" + name + "] " + msg, this);
                                // 보낸 사람에게 확인
                                out.println("전송되었습니다.");
                            } else {
                                out.println("빈 메시지는 전송되지 않습니다.");
                            }
                        }
                        default -> out.println("알 수 없는 명령입니다.");
                    }
                }
            } catch (IOException ignored) {
            } finally {
                // 정리 (idempotent 고려)
                CLIENTS.remove(this);
                if (this.name != null) NAMES.remove(this.name);
                August.day25.Multi.Server.broadcast("[서버] " + (name != null ? name : "알 수 없음") + "님 퇴장. 현재 " + NAMES.size() + "명");
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        // 개별 전송(출력 충돌 방지)
        void send(String message) {
            synchronized (out) {
                if (out == null) return;
                out.println(message);
                // PrintWriter는 예외를 던지지 않음 → checkError()로 실패 감지
                if (out.checkError()) {
                    disconnect(); // 소켓/리스트 정리(방송은 finally에서 1회만)
                }
            }
        }

        //연결 종료
        private void disconnect() {
            CLIENTS.remove(this);
            if (this.name != null) NAMES.remove(this.name);
            try { socket.close(); } catch (IOException ignore) {}
        }
    }
}
