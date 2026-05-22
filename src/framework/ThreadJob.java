package framework;

/**
 * ThreadJob 클래스는 스케줄 스레드에서 작업 스레드에 전달할 데이타를 저장하는 역할을 한다.
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public class ThreadJob {
	/**
	 * 스레드 인덱스(start 0)
	 */
	private int threadIndex = 0;
	/**
	 * 잡 인덱스(start 1)
	 */
	private long jobIndex = 0;
	/**
	 * 작업을 위한 데이터
	 */
	private Object object = null;
	/**
	 * 작업 스레드 종료 신호 여부
	 */
	private boolean shutdown = false;

	/**
	 * <p>
	 * ThreadJob 클래스 생성자
	 * </p>
	 * 
	 * @param threadIndex 스레드 인덱스 번호
	 * @param jobID       잡 인덱스 번호
	 * @param obj         작업을 위한 데이터
	 */
	ThreadJob(int threadIndex, long jobID, Object obj) {
		this.threadIndex = threadIndex;
		this.jobIndex = jobID;
		this.object = obj;
	}

	/**
	 * <p>
	 * 작업 스레드 종료 신호를 생성한다.
	 * </p>
	 * 
	 * @return ThreadJob 작업 스레드 종료 신호
	 */
	static ThreadJob shutdownJob() {
		ThreadJob threadJob = new ThreadJob(-1, -1, null);
		threadJob.shutdown = true;
		return threadJob;
	}

	/**
	 * @return the threadIndex
	 */
	public int getThreadIndex() {
		return threadIndex;
	}

	/**
	 * @param threadIndex the threadIndex to set
	 */
	public void setThreadIndex(int threadIndex) {
		this.threadIndex = threadIndex;
	}

	/**
	 * @return the jobIndex
	 */
	public long getJobIndex() {
		return jobIndex;
	}

	/**
	 * @return the object
	 */
	public Object getObject() {
		return object;
	}

	/**
	 * @return the shutdown
	 */
	public boolean isShutdown() {
		return shutdown;
	}

}
