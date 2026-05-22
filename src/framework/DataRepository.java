package framework;

/**
 * 처리 대상 데이터를 조회하기 위한 메서드를 정의하는 클래스
 * Repository는 파일, DB, 네트워크 등을 의미
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public interface DataRepository extends AutoCloseable {

	/**
	 * Repository에서 데이테를 읽기 위한 초기화 작업 정의 
	 */ 
	void init(Object repoInfo) throws Exception;

	/**
	 * 초기화된 Repository에서 데이터를 읽어오는 기능 정의
	 * 더 이상 데이터가 없는 경우 자원을 반납하는 기능 구현 필요
	 *   
	 * @return String 읽어들인 값
	 */
	Object getData();

	/**
	 * Repository에서 사용한 자원을 반납한다.
	 */
	void close();

}
