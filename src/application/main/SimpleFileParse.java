package application.main;

import application.repository.MMapStringParser;
import application.worker.JeongSanFileWorker;
import framework.DataRepository;
import framework.ThreadPoolFramework;
import framework.ThreadWorker;

/**
 * 메모리 맵 방식으로 파일을 읽어 문자열 처리 작업을 실행하는 예제 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link ThreadPoolFramework}, {@link JeongSanFileWorker}, {@link MMapStringParser}
 */
public class SimpleFileParse {
	/**
	 * <p>
	 * 간단한 파일 파싱 예제를 실행한다.
	 * </p>
	 * 
	 * @param args 실행 인자
	 */
	public static void main(String[] args) {
		System.out.println("=================================");
		System.out.println("   SimpleFileWork Start!!");
		System.out.println("=================================");
		
		String filePath = "";
		ThreadPoolFramework threadPoolFrame = null;
		
		if(filePath.length() <= 0)
			filePath = "fixtures/test2.txt";
		
		ThreadWorker jeongSanWorker = new JeongSanFileWorker();
		DataRepository dataRepo = new MMapStringParser();

		try{
			dataRepo.init(filePath);
			threadPoolFrame = new ThreadPoolFramework(jeongSanWorker, dataRepo, 5);
			
		}catch(Exception e){
			System.out.println("SimpleFileWork.main.e:"+e.getMessage());
			try{
				if(threadPoolFrame != null)
					threadPoolFrame.finishingJob();
			}catch(Exception e2){
				System.out.println("SimpleFileWork.main.e2:"+e2.getMessage());
			}
		}
		
		
		//wait main thread end
		while(threadPoolFrame != null && !threadPoolFrame.getCheckEOF()){
			try{
				Thread.sleep(100);
			}catch(Exception e){}
		}
		
		try{
			if(threadPoolFrame != null)
				threadPoolFrame.finishingJob();
		}catch(Exception e){
			System.out.println("SimpleFileWork.main.e3:"+e.getMessage());
		}
		
		System.out.println("SimpleFileWork End!!");
	}
}
