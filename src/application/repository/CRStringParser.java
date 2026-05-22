package application.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
public class CRStringParser implements DataRepository{
	private BufferedReader bufferReader = null;
	private File crFile = null;
	
	public void init(Object repoInfo) throws Exception{
		if(bufferReader == null){
			crFile = new File(repoInfo.toString());

			System.out.println("File Path "+crFile.toString());
			bufferReader = new BufferedReader(new FileReader(crFile));
		}
	}
	
	public Object getData(){
		
		String readLine = null;
		
		try{
			readLine = bufferReader.readLine();
			if(readLine == null){
				close();
			}
		}catch(Exception e){
			System.out.println("EOF or Exception CRStringFilter.readLine:"+e.getMessage());
			close();
		}
		
		return readLine;
	}
	
	public void close(){
		try{
			if(bufferReader != null){
				bufferReader.close();
				bufferReader = null;
			}
		}catch(Exception e){}
	}
}
