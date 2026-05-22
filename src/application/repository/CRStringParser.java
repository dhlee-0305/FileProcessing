package application.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import framework.DataRepository;

/**
 * '/n'을 이용하여 파일에서 문자열을 읽어오는 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link DataRepository}
 */
public class CRStringParser implements DataRepository {
	private BufferedReader bufferReader = null;
	private File crFile = null;

	/**
	 * <p>
	 * 줄 단위로 읽을 파일을 초기화한다.
	 * </p>
	 * 
	 * @param repoInfo 파일 경로
	 * @throws Exception 파일 초기화 시 에러 발생
	 */
	public void init(Object repoInfo) throws Exception {
		if (bufferReader == null) {
			crFile = new File(repoInfo.toString());

			System.out.println("File Path " + crFile.toString());
			bufferReader = new BufferedReader(new FileReader(crFile));
		}
	}

	/**
	 * <p>
	 * 파일에서 한 줄의 문자열을 읽어온다.
	 * </p>
	 * 
	 * @return Object 읽어들인 문자열, EOF인 경우 null
	 * @throws Exception 파일 읽기 시 에러 발생
	 */
	public Object getData() throws Exception {
		String readLine = bufferReader.readLine();
		if (readLine == null) {
			close();
		}

		return readLine;
	}

	/**
	 * <p>
	 * 파일 읽기에 사용한 자원을 반납한다.
	 * </p>
	 */
	public void close() {
		try {
			if (bufferReader != null) {
				bufferReader.close();
				bufferReader = null;
			}
		} catch (Exception e) {
		}
	}
}
