package application.worker;

import framework.ThreadJob;
import framework.ThreadWorker;

/**
 * Test Class
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link ThreadWorker}
 */

public class JeongSanFileWorker implements ThreadWorker{
	/**
	 * <p>
	 * 스레드로부터 전달받은 문자열 작업을 처리한다.
	 * </p>
	 * 
	 * @param threadJob 스케줄러가 작업 스레드에 전달하는 데이터 클래스
	 */
	public void serve(ThreadJob threadJob){
		int threadIndex = threadJob.getThreadIndex();
		long countIndex = threadJob.getJobIndex();
		String str = threadJob.getObject().toString();
		
		System.out.println("T"+String.format("%03d", threadIndex)+" ("+String.format("%03d", countIndex)+") JeongSan("+str+")");
		//System.out.println("body :"+parseData.getbody());
		
		//try{ TimeUnit.MILLISECONDS.sleep(2000); } catch(Exception e){}
	}
	
	/**
	 * <p>
	 * 최종 작업 결과를 출력한다.
	 * </p>
	 * 
	 * @return String 최종 작업 결과 정리 내역
	 */
	public String getSummary(){
		return "";
	}
}
