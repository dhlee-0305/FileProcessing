package framework;


/**
 * ThreadRunner 클래스는 스케줄 스레드에서 데이타를 전달 받아 작업을 실행하는 기능을 구현한다.<br>
 * 실제 작업은 ThreadWorker 인터페이스를 구현한 클래스를 작업에 이용한다.
 * 
 * <pre>
 * 1) 작업 Queue에 데이타가 들어올때까지 wait()
 * 2) notify()를 수신하면 작업 클래스를 통해 데이타 처리
 * </pre>
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public class ThreadRunner extends Thread{
	/**
	 * 스레드 인덱스(0 ~ MAX-1)
	 */
	private int threadIndex = 0;
	/**
	 * 스레드에 데이터를 전달하기 위한 컬렉션
	 */
	private ThreadData jobQueue = null;
	/**
	 * 스레드 상태
	 */
	private volatile boolean status = false;
	/**
	 * 작업 클래스
	 */
	private ThreadWorker threadWorker;

	/**
	 * 작업 실행 중 발생한 예외
	 */
	private volatile Exception workerException = null;
	
	
	/**
	 * <p>
	 * ThreadRunner 클래스 생성자
	 * </p>
	 * 
	 * @param threadIndex 스레드 인덱스 번호
	 * @param threadData 작업 데이터를 전달하기 위한 Queue
	 * @param threadWorker 실제 작업을 실행할 클래스
	 */
	ThreadRunner(int threadIndex, ThreadData threadData, ThreadWorker threadWorker){
		this.threadIndex = threadIndex;
		this.jobQueue = threadData;
		this.status = true;
		this.threadWorker = threadWorker;
	}
	
	/**
	 * <p>
	 * 작업 Queue에서 데이터를 받아 워커 작업을 실행한다.
	 * </p>
	 */
	public void run(){
		while(!Thread.interrupted()){
			try{
				// 1)ThreadJob 클래스에서 데이터를 받아옴 - 데이터가 없는 경우 대기함
				ThreadJob threadJob = jobQueue.take();
				
				// 2)스레드 인덱스, 잡인덱스를 추가하여 데이터 전달(스레드인덱스, 작업인덱스, Data......)
				//this.threadWorker.serve(threadIndex+","+data.getJobIndex()+","+data.getObject());
				this.threadWorker.serve(threadJob);
				
				Thread.yield();
			}catch(InterruptedException ie){
				return;
			}catch(Exception e){
				this.workerException = e;
				System.out.println("ServerRunner : "+e.getMessage());
			}finally{
				// 3) 작업 완료 후 준비 상태로 스레드 상태 변경
				this.setReady();
			}
		}
	}

	/**
	 * <p>
	 * 스레드 인덱스 번호를 반환한다.
	 * </p>
	 * 
	 * @return int 스레드 인덱스 번호
	 */
	public int getThreadIndex(){
		return threadIndex;
	}
	
	/**
	 * <p>
	 * 작업 스레드가 작업을 받을 수 있는 상태인지 반환한다.
	 * </p>
	 * 
	 * @return boolean 작업 가능 여부
	 */
	public boolean isReady(){
		return status;
	}
	
	/**
	 * <p>
	 * 작업 스레드를 작업 중 상태로 변경한다.
	 * </p>
	 */
	public void setBusy(){
		status = false;
	}
	
	/**
	 * <p>
	 * 작업 스레드를 작업 가능 상태로 변경한다.
	 * </p>
	 */
	void setReady(){
		this.status = true;
	}

	/**
	 * <p>
	 * 작업 실행 중 발생한 예외를 반환한다.
	 * </p>
	 * 
	 * @return Exception 작업 실행 중 발생한 예외
	 */
	public Exception getWorkerException(){
		return this.workerException;
	}
}
