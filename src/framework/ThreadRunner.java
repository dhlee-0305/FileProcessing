package framework;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ThreadRunner 클래스는 스케줄 스레드에서 데이타를 전달 받아 작업을 실행하는 기능을 구현한다.<br>
 * 실제 작업은 ThreadWorker 인터페이스를 구현한 클래스를 작업에 이용한다.
 * 
 * <pre>
 * 1) 공유 작업 Queue에서 데이타가 들어올때까지 poll()
 * 2) 작업 클래스를 통해 데이타 처리
 * 3) 스케줄러가 종료되고 Queue가 비면 스레드 종료
 * </pre>
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public class ThreadRunner extends Thread {
	/**
	 * 스레드 인덱스(0 ~ MAX-1)
	 */
	private int threadIndex = 0;
	/**
	 * 스레드에 데이터를 전달하기 위한 컬렉션
	 */
	private ThreadData jobQueue = null;
	/**
	 * 작업 클래스
	 */
	private ThreadWorker threadWorker;

	/**
	 * 작업 실행 중 발생한 예외
	 */
	private AtomicReference<Exception> workerException = null;
	/**
	 * 스케줄러가 더 이상 작업을 추가하지 않는지 확인하는 변수
	 */
	private AtomicBoolean schedulerFinished = null;

	/**
	 * <p>
	 * ThreadRunner 클래스 생성자
	 * </p>
	 * 
	 * @param threadIndex  스레드 인덱스 번호
	 * @param threadData   작업 데이터를 전달하기 위한 Queue
	 * @param threadWorker 실제 작업을 실행할 클래스
	 */
	ThreadRunner(int threadIndex, ThreadData threadData, ThreadWorker threadWorker,
			AtomicReference<Exception> workerException, AtomicBoolean schedulerFinished) {
		this.threadIndex = threadIndex;
		this.jobQueue = threadData;
		this.threadWorker = threadWorker;
		this.workerException = workerException;
		this.schedulerFinished = schedulerFinished;
	}

	/**
	 * <p>
	 * 작업 Queue에서 데이터를 받아 워커 작업을 실행한다.
	 * </p>
	 */
	public void run() {
		while (true) {
			try {
				// 1)ThreadJob 클래스에서 데이터를 받아옴 - 데이터가 없는 경우 짧게 대기함
				ThreadJob threadJob = jobQueue.poll(100, TimeUnit.MILLISECONDS);
				if (threadJob == null) {
					if (schedulerFinished.get() && jobQueue.isEmpty()) {
						return;
					}
					continue;
				}
				threadJob.setThreadIndex(threadIndex);

				// 2)스레드 인덱스, 잡인덱스를 추가하여 데이터 전달(스레드인덱스, 작업인덱스, Data......)
				// this.threadWorker.serve(threadIndex+","+data.getJobIndex()+","+data.getObject());
				this.threadWorker.serve(threadJob);

				Thread.yield();
			} catch (InterruptedException ie) {
				return;
			} catch (Exception e) {
				this.workerException.compareAndSet(null, e);
				System.out.println("ServerRunner : " + e.getMessage());
			}
		}
	}
}
