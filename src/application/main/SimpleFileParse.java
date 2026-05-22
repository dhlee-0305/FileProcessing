package application.main;

import application.repository.MMapStringParser;
import application.worker.JeongSanFileWorker;
import framework.DataRepository;
import framework.ThreadPoolFramework;
import framework.ThreadWorker;

public class SimpleFileParse {
	public static void main(String[] args) {
		System.out.println("=================================");
		System.out.println("   SimpleFileWork Start!!");
		System.out.println("=================================");
		
		String filePath = "";
		ThreadPoolFramework threadPoolFrame = null;
		
		if(filePath.length() <= 0)
			//filePath = "c:/eclipse/workspace/FileProcessing/test.txt";
			filePath = "test2.txt";
		
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
		
		System.out.println("SimpleFileWork End!!");
	}
}
