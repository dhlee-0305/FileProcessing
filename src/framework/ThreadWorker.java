package framework;

/**
 * 실제 작업을 구현하기 위한 인터페이스 정의<br>
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public interface ThreadWorker {
	/**
	 * 스레드로 부터 데이터를 전달 받아 처리하는 기능을 담당하는 함수
	 *   
	 * @param ThreadJob 스케줄러가 작업 스레드에 전달하는 데이터 클래스
	 * @throws Exception 작업 처리 중 발생한 예외
	 */
	void serve(ThreadJob threadJob) throws Exception;
	
	/**
	 * 최종 작업 결과를 출력해주는 함수
	 * 
	 * @return String 최종 작업 결과 정리 내역 
	 */
	public String getSummary();
}
