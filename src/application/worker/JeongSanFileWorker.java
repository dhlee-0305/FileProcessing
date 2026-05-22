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
	public void serve(ThreadJob threadJob){
		int threadIndex = threadJob.getThreadIndex();
		long countIndex = threadJob.getJobIndex();
		String str = threadJob.getObject().toString();
		
		System.out.println("T"+String.format("%03d", threadIndex)+" ("+String.format("%03d", countIndex)+") JeongSan("+str+")");
		//System.out.println("body :"+parseData.getbody());
		
		//try{ TimeUnit.MILLISECONDS.sleep(2000); } catch(Exception e){}
	}
	
	public String getSummary(){
		return "";
	}
}
