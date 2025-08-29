package August.day25.Multi;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    static final private int PORT = 5000;  // 포트번호 설정 
    // 다중 접속을 TreadPool로 관리
    static final private ExecutorService POOL = Executors.newCachedThreadPool();  
    // 사용자 번호 자동 증가
    static final private AtomicInteger CLIENT_SEQ = new AtomicInteger(1);

    // 사용자 이름을 저장할 List
    private static final List<String> NAMES = new CopyOnWriteArrayList<>();
    // 클라이언트를 관리할 List
    private static final List<ClientHandler> CLIENTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("[서버] 서버 시작");
        //서버 소켓 생성
        ServerSocket serverSocket = new ServerSocket(PORT);

        // Ctrl + C 입력시 종료 
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[서버] 서버 종료...");
            try {
                POOL.shutdownNow();
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));

        while (true) {
            // 클라이언트 연결 기다리기
            Socket clientSocket = serverSocket.accept();
            System.out.println("[서버] 클라이언트와 연결되었습니다.");
            int seq = CLIENT_SEQ.getAndIncrement();
            
            //스레드 풀에 클라이언트 정보 저장
            POOL.submit(new ClientHandler(clientSocket, seq));
        }
    }

    // 전체에게 전송
    static void broadcast(String message) {
        for (ClientHandler ch : CLIENTS) ch.send(message);
    }

    // 보낸 사람 제외 전송
    static void broadcastExcept(String message, ClientHandler except) {
        for (ClientHandler ch : CLIENTS) if (ch != except) ch.send(message);
    }


    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int sequence;
        private BufferedReader in;
        private PrintWriter out;
        private String name;

        public ClientHandler(Socket socket, int sequence) {
            this.socket = socket;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            try {
                // 클라이언트 Request 받는 InputStreamReader
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                // 클라이언트에게 Response 를 하기 위한 OutputStream
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 닉네임 수신 및 등록
                String clientName = in.readLine();
                if (clientName == null || clientName.isBlank()) {
                    out.println("닉네임이 올바르지 않습니다. 연결을 종료합니다.");
                    return;
                }
                // 중복 체크(단순 거부)
                if (NAMES.contains(clientName)) {
                    out.println("이미 존재하는 닉네임입니다. 다른 이름으로 접속하세요.");
                    return;
                }
                this.name = clientName;

                NAMES.add(name);
                CLIENTS.add(this);

                out.println(name + "님 반갑습니다! (/quit 종료)");
                Server.broadcast("[서버] " + name + "님 입장. 현재 " + NAMES.size() + "명");

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
                                Server.broadcastExcept("[" + name + "] " + msg, this);
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
                // 정리
                CLIENTS.remove(this); // 클라이언트 배열에서 삭제
                NAMES.remove(this.name); // 이름 배열에서 사용자 닉네임 삭제
                Server.broadcast("[서버] " + name + "님 퇴장. 현재 " + NAMES.size() + "명");
                try { socket.close(); } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 개별 전송(충돌 방지)
        void send(String message) {
            synchronized (out) {
                out.println(message);
                if (out.checkError()) {
                    CLIENTS.remove(this);
                    NAMES.remove(this.name);
                    try { socket.close(); } catch (IOException ignore) {}
                }
            }
        }
    }
}
