package com.example.workguard.Client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class PythonServerRunner {

    private volatile Process pythonProcess;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void startPythonServer() {

        // 1) 8081 포트가 이미 사용 중이면 → 기존 Python 서버 사용
        if (isPortInUse(8081)) {
            System.out.println("⚠️ 포트 8081 이미 사용 중 → 기존 Python 서버 재사용 (새로 실행 안 함)");
            return;
        }

        // 2) 기존 서버 없음 → Python FastAPI(Uvicorn) 서버 새로 실행
        System.out.println("🔥 기존 Python 서버 없음 → FastAPI(Uvicorn) 새로 실행");

        startPythonProcess();
    }

    /**
     * Python 서버 실행 함수
     */
    private void startPythonProcess() {
        String pythonExe = "python";
        String baseDir = System.getProperty("user.dir");
        String scriptDir = baseDir + "/src/main/resources/models/ai";

        ProcessBuilder pb = new ProcessBuilder(
                pythonExe,
                "-m", "uvicorn",
                "summary_api:app",
                "--host", "0.0.0.0",
                "--port", "8081"
        );

        pb.directory(new File(scriptDir));  // 반드시 summary_api.py가 있는 폴더로 설정
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        executor.submit(() -> {
            try {
                pythonProcess = pb.start();
                pythonProcess.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("✔ Python 서버 실행 시작됨 (포트 8081)");
    }


    /**
     * 8081 포트 사용 여부 체크 함수
     * 포트가 비어있으면 false, 사용 중이면 true 반환
     */
    private boolean isPortInUse(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return false; // 포트 비어 있음
        } catch (IOException e) {
            return true;  // 포트 사용 중
        }
    }

    @PreDestroy
    public void stopPythonServer() {
        // Spring 종료 시에만 직접 실행한 Python 서버를 종료
        if (pythonProcess != null && pythonProcess.isAlive()) {
            System.out.println("💀 Spring 종료 → Python 서버 강제 종료");
            pythonProcess.destroyForcibly();
            System.out.println("✅ Python 서버 종료 완료");
        }
        executor.shutdownNow();
    }
}
