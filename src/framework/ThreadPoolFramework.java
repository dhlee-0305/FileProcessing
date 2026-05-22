package framework;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ThreadPoolFrame 클래스는 파일 처리를 위한 Thread Pool Framework을 제공한다.<br>
 * 이 클래스를 이용하기 위해서는 ThreadWorker 인터페이스와 StringParser 인터페이스를 구현한 클래스가 필요하다.<br>
 * ThreadWorker 인터페이스는 작업을 정의하기 위한 인터페이스이고,<br>
 * DataRepository 인터페이스는 파일에서 데이터를 읽어오기 위한 기능을 위한 인터페이스이다.<br>
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link ThreadWorker}, {@link DataRepository}, {@link ThreadData}
 */
public class ThreadPoolFramework {
	/** 스케줄 스레드 멤버 변수 */
	private Thread schedulerThread = null;

	/** 파일 작업 클래스 멤버 변수 */
	private ThreadWorker threadWorker = null;

	/** 파일 해석 클래스 멤버 변수 */
	private DataRepository dataRepo = null;

	/** 파일 종료 확인 변수 */
	private volatile boolean checkEOF = false;

	/** 스케줄 스레드에서 발생한 예외 */
	private volatile Exception schedulerException = null;

	/** 작업 스레드에서 발생한 예외 */
	private AtomicReference<Exception> workerException = new AtomicReference<Exception>();

	/** 작업 스케줄 리스트 멤버 변수 */
	private LinkedList<ThreadRunner> THREAD_POOL = new LinkedList<ThreadRunner>();

	/** 작업 스레드에 전달할 공유 Queue */
	private ThreadData jobQueue = null;

	/** 작업 스레드 개수 */
	private int maxThreadCount = 0;

	/**
	 * <p>
	 * ThreadPoolFrame 클래스 생성자
	 * </p>
	 * 
	 * @param threadWorker   실제 작업을 실행할 클래스
	 * @param dataRepo       파일 해석 클래스
	 * @param maxThreadCount 스레드 개수
	 * @throws Exception 스레드 풀 생성 시 에러 발생
	 * 
	 */
	public ThreadPoolFramework(
			ThreadWorker threadWorker,
			DataRepository dataRepo,
			int maxThreadCount) throws Exception {

		if (threadWorker == null) {
			throw new IllegalArgumentException("threadWorker is null");
		}
		if (dataRepo == null) {
			throw new IllegalArgumentException("dataRepo is null");
		}
		if (maxThreadCount <= 0) {
			throw new IllegalArgumentException("maxThreadCount must be greater than 0");
		}

		this.threadWorker = threadWorker;
		this.dataRepo = dataRepo;
		this.maxThreadCount = maxThreadCount;

		jobQueue = new ThreadData(maxThreadCount * 2);

		// work Thread Pool create
		for (int i = 0; i < maxThreadCount; i++) {
			createThreadPool(i);
		}

		System.out.println("Total " + THREAD_POOL.size() + " Thread Created!");

		Thread.sleep(1000 * 1);

		// Scheduler Thread create - Work Start
		schedulerThread = new Thread(new Runnable() {
			public void run() {
				threadScheduler();
			}
		});
		schedulerThread.setDaemon(true); // Spring Batch로 구현 시 삭제 필요
		schedulerThread.start();
	}

	/**
	 * <p>
	 * 작업 스레드 풀을 생성한다.
	 * </p>
	 * 
	 * @param int idx 스레드 인덱스 번호
	 */
	private void createThreadPool(int threadIndex) {
		ThreadRunner threadRunner = new ThreadRunner(threadIndex, jobQueue, this.threadWorker, this.workerException);

		synchronized (THREAD_POOL) {
			THREAD_POOL.add(threadRunner);
		}
		threadRunner.setDaemon(true);
		threadRunner.start();
	}

	/**
	 * <p>
	 * 파일로 부터 데이타를 읽어와 작업 스레드에 작업을 할당한다.
	 * </p>
	 */
	private void threadScheduler() {
		try {
			for (long i = 0;; i++) {
				Object readData = dataRepo.getData();
				if (readData == null) {
					// EOF
					System.out.println("threadScheduler(1) EOF");
					return;
				}

				String readString = readData.toString();

				if (readString != null && readString.length() > 0) {
					// 작업 전달
					pushJob(i, readString);
				}
			}
		} catch (Exception e) {
			System.out.println("threadScheduler Exception:" + e.getMessage());
			this.schedulerException = e;
			return;
		} finally {
			pushShutdownJobs();
			this.checkEOF = true;
		}
	}

	/**
	 * <p>
	 * 작업 스레드에 작업을 할당한다.
	 * </p>
	 * 
	 * <pre>
	 * 1) 전송할 스레드 잡 생성
	 * 2) 공유 Queue에 데이타 put
	 * </pre>
	 * 
	 * @param long   jobIndex 스레드에 전달할 Job Index
	 * @param Object obj 스레드에 전달할 데이타 Object
	 */
	private void pushJob(long jobIndex, Object obj) throws Exception {
		ThreadJob threadJob = new ThreadJob(-1, jobIndex, obj);
		jobQueue.put(threadJob);
	}

	/**
	 * <p>
	 * 모든 작업 스레드에 종료 신호를 전달한다.
	 * </p>
	 */
	private void pushShutdownJobs() {
		for (int i = 0; i < maxThreadCount; i++) {
			try {
				jobQueue.put(ThreadJob.shutdownJob());
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				if (this.schedulerException == null) {
					this.schedulerException = ie;
				}
				return;
			}
		}
	}

	/**
	 * <p>
	 * 스케줄 스레드 작업 종료
	 * </p>
	 * 
	 * <pre>
	 * 1) 입력 데이타를 전부 읽었는지 확인
	 * 2) 작업 스레드 종료 대기
	 * 3) 스케줄 스레드 종료
	 * </pre>
	 * 
	 * @throws Exception 스케줄 스레드 종료시 에러 발생
	 */
	public void finishingJob() throws Exception {
		try {
			// 입력 데이타를 전부 읽었는지 확인
			while (!this.getCheckEOF()) {
				schedulerThread.join();
			}

			// 작업 스레드 종료 대기
			waitAllThread();

			if (this.schedulerException != null) {
				throw this.schedulerException;
			}

			if (this.workerException.get() != null) {
				throw this.workerException.get();
			}
		} finally {
			if (dataRepo != null)
				dataRepo.close();
		}
	}

	/**
	 * <p>
	 * 작업 스레드 작업 종료 대기
	 * </p>
	 * 
	 * <pre>
	 * 작업 스레드 종료 상태 대기
	 * </pre>
	 * 
	 * @throws InterruptedException 작업 스레드 종료시 에러 발생
	 */
	private void waitAllThread() throws InterruptedException {
		while (THREAD_POOL.size() > 0) {
			ThreadRunner thread = null;
			synchronized (THREAD_POOL) {
				thread = (ThreadRunner) THREAD_POOL.removeFirst();
			}
			thread.join();
			System.out.print(">");
		}
	}

	/**
	 * <p>
	 * 입력데이터 종료를 설정
	 * </p>
	 * 
	 */
	public boolean getCheckEOF() {
		return this.checkEOF;
	}
}
