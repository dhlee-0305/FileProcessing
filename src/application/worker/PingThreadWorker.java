package application.worker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

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
		InputStream processInputStream = null;
		InputStream processErrorStream = null;
	
		int threadIndex = threadJob.getThreadIndex();
		long countIndex = threadJob.getJobIndex();
		String ipAddress = threadJob.getObject().toString();
		
		//System.out.println("index:"+countIndex+", ip:"+ipAddress);
		// ping 대상에 명령 구분자가 포함되지 않도록 허용 문자만 처리한다.
		if(!isValidPingTarget(ipAddress)){
			addFailCount();
			System.out.println("T"+String.format("%03d", threadIndex)+" ("+String.format("%03d", countIndex)+") PingTest ipAddress("+ipAddress+") --> 실패");
			return;
		}
				
		try{
			byte[] msg = new byte[1024];
			int readLength;
			
			ProcessBuilder processBuilder = createPingProcessBuilder(ipAddress);
			processBuilder.redirectErrorStream(true);
			process = processBuilder.start();
			process.getOutputStream().close();
			processInputStream = process.getInputStream();
			processErrorStream = process.getErrorStream();
			
			if(!process.waitFor(500, TimeUnit.MILLISECONDS)){
				process.destroyForcibly();
				process.waitFor();
			}
			
            while((readLength=processInputStream.read(msg)) > 0) {	// process결과 읽기
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
				
			}finally{
				try{ if(processInputStream != null) processInputStream.close(); }catch(Exception e){}
				try{ if(processErrorStream != null) processErrorStream.close(); }catch(Exception e){}
				if(process != null)
					process.destroy();
			}
		
		String Result = "실패";
		if(isOK){
			Result = "성공";
			addSuccessCount();
		}else{
			addFailCount();
		}
		
		System.out.println("T"+String.format("%03d", threadIndex)+" ("+String.format("%03d", countIndex)+") PingTest ipAddress("+ipAddress+") --> "+Result);

		}
		
	/**
	 * 운영체제에 맞는 ping 실행 명령을 생성한다.
	 * shell을 거치지 않고 인자를 분리하여 전달한다.
	 * 
	 * @param ipAddress ping 대상 주소
	 * @return ProcessBuilder ping 실행 명령
	 */
	private ProcessBuilder createPingProcessBuilder(String ipAddress){
		if(isWindows()){
			return new ProcessBuilder("ping", "-n", "1", ipAddress);
		}
		return new ProcessBuilder("ping", "-c", "1", ipAddress);
	}
	
	/**
	 * 현재 실행 환경이 Windows인지 확인한다.
	 * 
	 * @return boolean Windows 여부
	 */
	private boolean isWindows(){
		String osName = System.getProperty("os.name");
		return osName != null && osName.toLowerCase().contains("win");
	}
	
	/**
	 * ping 대상 주소로 사용할 수 있는 문자열인지 확인한다.
	 * 
	 * @param target ping 대상 주소
	 * @return boolean 유효 여부
	 */
	private boolean isValidPingTarget(String target){
		return target != null && target.matches("[A-Za-z0-9._:-]+");
	}
	
	/**
	 * 성공 카운트를 증가시킨다.
	 * 여러 작업 스레드에서 공유하므로 동기화한다.
	 */
	private synchronized void addSuccessCount(){
		successCount++;
		totalCount++;
	}
	
	/**
	 * 실패 카운트를 증가시킨다.
	 * 여러 작업 스레드에서 공유하므로 동기화한다.
	 */
	private synchronized void addFailCount(){
		failCount++;
		totalCount++;
	}
	    
	public synchronized String getSummary(){
		return "Total:"+totalCount+", Success:"+successCount+", Fail:"+failCount;
	}
}
