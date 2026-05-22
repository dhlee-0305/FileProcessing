package application.worker;

import java.io.ByteArrayOutputStream;

import framework.ThreadJob;
import framework.ThreadWorker;

/**
 * 파일에 있는 ip리스트를 이용하여 ping을 전송하여 서버 접근여부를 테스트 하는 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link ThreadWorker}
 */
public class PingThreadWorker implements ThreadWorker{
	private final String pingCmdStr = "cmd /c chcp 437 & ping -n 1 "; 			// cmd 명령어 : "ping -c 1"
	private final String pingSuccessLoss = "0% loss"; 							// cmd 결과 : "0% packet loss";
	private final String pingSuccessRoundTrip = "round trip";					// cmd 결과 : "approximate round trip";
	private final String pingTimeOut = "Request timed out"; 					// cmd 결과 : "Request timed out.";
	private final String pingUnreachable = "Destination host unreachable"; 		// cmd 결과 : "Destination host unreachable.";
	
	private long successCount = 0;
	private long failCount = 0;
	private long totalCount = 0;
	
	public void serve(ThreadJob threadJob){
		ByteArrayOutputStream pingResultByteArray = new ByteArrayOutputStream();
		boolean isOK = false;
		Process process = null;
		long processTime;
		long timeoutInMillis;
		long finish;

		int threadIndex = threadJob.getThreadIndex();
		long countIndex = threadJob.getJobIndex();
		String ipAddress = threadJob.getObject().toString();
		
		String cmdString = pingCmdStr+ipAddress;
		
		//System.out.println("index:"+countIndex+", ip:"+ipAddress);
		//System.out.println("PING:"+cmdString);
				
		try{
			byte[] msg = new byte[1024];
			int readLength;
			
			processTime = System.currentTimeMillis(); 				// 현재시간
			timeoutInMillis = (long) (1000L * 0.5);  				//timeout으로 지정할 시간 (1000 * 원하는 초)
			finish = processTime + timeoutInMillis; 				// cmd 명령 실행 이후 timeout이 되는 시간
			process = Runtime.getRuntime().exec(cmdString) ; 		//cmd 명령 실행
					
			while(processIsAlive(process)){ 						// process가 살아있는지 확인
    			Thread.sleep(100); 									// process가 작동을 끝낼때까지 대기
    			if(System.currentTimeMillis() > finish){ 			// 타임아웃 시간을 넘겼는지 확인
    				process.destroy(); 								// process 강제종료
    			}
    		}
			
            while((readLength=process.getInputStream().read(msg)) > 0) {	// process결과 읽기
            	pingResultByteArray.write(msg, 0, readLength);
            }
            
            // 타임아웃과 연결실패가 없고, 손실률0%에 왕복 시간이 출력된다면 정상으로 처리한다.
            if(	!new String(pingResultByteArray.toString()).contains(pingTimeOut) &&
            	!new String(pingResultByteArray.toString()).contains(pingUnreachable) &&
            	 new String(pingResultByteArray.toString()).contains(pingSuccessLoss) &&
            	 new String(pingResultByteArray.toString()).contains(pingSuccessRoundTrip)){ 
            	
        		isOK = true;
        	}
    		
    		pingResultByteArray.close();            
		}catch(Exception e){
			
		}
		
		String Result = "실패";
		if(isOK){
			Result = "성공";
			successCount++;
			totalCount++;
		}else{
			failCount++;
			totalCount++;
		}
		
		System.out.println("T"+String.format("%03d", threadIndex)+" ("+String.format("%03d", countIndex)+") PingTest ipAddress("+ipAddress+") --> "+Result);

	}
	
    /**
     * processIsAlive
     * Process가 죽었는지, 살았는지를 check한다. (timeout 확인을 위해 사용)
     * process.exitValue() 사용시 process가 종료되지 않을경우 IllegalThreadStateException이 발생하는것을 이용.
     * @param Process
     * @return boolean
     * 
     * @author yjchoi
     * @since 2013.07.11
     */
    public boolean processIsAlive(Process process){
    	try{
	    	process.exitValue(); 					// sub process가 정상종료될때 0을 return.
	    	return false;
    	}catch(IllegalThreadStateException itse){ 	// process가 살아있는경우
    		return true;
    	}
    }
    
    public String getSummary(){
    	return "Total:"+totalCount+", Success:"+successCount+", Fail:"+failCount;
    }
}
