package application.main;

import application.repository.CRStringParser;
import application.worker.PingThreadWorker;
import framework.DataRepository;
import framework.ThreadPoolFramework;
import framework.ThreadWorker;

/**
 * 파일에 정의된 IP 목록을 이용하여 Ping 테스트를 실행하는 예제 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link ThreadPoolFramework}, {@link PingThreadWorker}, {@link CRStringParser}
 */
public class PingTest {
	/**
	 * <p>
	 * Ping 테스트 예제를 실행한다.
	 * </p>
	 * 
	 * @param args 입력 파일 경로와 작업 스레드 개수
	 */
	public static void main(String[] args) {
		System.out.println("=================================");
		System.out.println("     Ping Test Start!!");
		System.out.println("=================================");
		
		String filePath = "";
		int maxThreadCount = 0;
		ThreadPoolFramework threadPoolFrame = null;
		
		// Argument가 있다면 읽어와 파일경로와 스레드 개수에 설정해 준다.
		try{
			if(args.length == 2){
				filePath = args[0];
				maxThreadCount = Integer.parseInt(args[1]);
			}else if(args.length == 0){
				// Argument가 없다면 기본 값 사용
				if(filePath.length() <= 0)
					filePath = "fixtures/ping_100.txt";
	
				if(maxThreadCount == 0)
					maxThreadCount = 10;
			}else{
				System.out.println("Usage: java -cp bin application.main.PingTest <filePath> <threadCount>");
				return;
			}

			if(maxThreadCount <= 0){
				System.out.println("threadCount must be greater than 0");
				return;
			}
		}catch(NumberFormatException e){
			System.out.println("threadCount must be a number");
			return;
		}
				
		ThreadWorker threadWorker = new PingThreadWorker();
		DataRepository dataRepo = new CRStringParser();

		try{

			dataRepo.init(filePath);
			threadPoolFrame = new ThreadPoolFramework(threadWorker, dataRepo, maxThreadCount);
			
		}catch(Exception e1){
			System.out.println("PingTest.main.e1:"+e1.getMessage());

			try{
				if(threadPoolFrame != null)
					threadPoolFrame.finishingJob();
			}catch(Exception e2){
				System.out.println("PingTest.main.e2:"+e2.getMessage());
			}
		}
		
		// 작업 스레드가 모두 종료되길 기다림(누락하면 결과 달라짐)
		try{
			threadPoolFrame.finishingJob();
		}catch(Exception e3){
			System.out.println("PingTest.main.e3:"+e3.getMessage());
		}
		
		System.out.println("\n"+threadWorker.getSummary());
		System.out.println("Ping Test End!!");
	}
}
