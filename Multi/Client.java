package August.day25.Multi;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        //서버에 연결
        try (Socket socket = new Socket(host, port);
             // 서버 응답 을 받는 BufferedReader
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             // 서버로 요청을 보내기 위한 PrintWriter
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             // 사용자 입력을 받기 위한 BufferedReader
             BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
        ) {
            // 서버 연결 확인 
            System.out.println("[클라이언트] 연결되었습니다. " + host + ":" + port);

            // 닉네임 입력
            System.out.print("본인의 닉네임을 입력하세요> ");
            String name = keyboard.readLine();
            out.println(name);

            //  수신 전용 스레드: 서버가 아무 때나 보내는 방송을 계속 출력
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException ignored) {}
            }, "server-listener");
            listener.setDaemon(true); //데몬 스레드로 설정 다른 메인 스레드가 종료시 자동 종료
            listener.start();

            // 서버에게 송신
            while (true) {
                System.out.println("요청을 입력하세요 (/quit: 종료, /who: 현재 접속 인원, /text: 메시지 전달)");
                // 사용자 입력 받기
                String command = keyboard.readLine();
                if (command == null || command.isBlank()) {
                    System.out.println("공백은 입력하실 수 없습니다.");
                    continue;
                }

                //서버에게 전달
                out.println(command);

                // 사용자가 /text일 경우
                if ("/text".equals(command)) {
                    // 서버가 "메시지를 입력하세요:" 를 방송/응답으로 보냄 → listener가 화면에 출력함
                    String message = keyboard.readLine(); // 사용자가 실제 메시지 입력
                    if (message != null) out.println(message);
                    // 이후 "전송되었습니다."도 listener가 출력
                }

                if ("/quit".equals(command)) {
                    break; // 종료
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
