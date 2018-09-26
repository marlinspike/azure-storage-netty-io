package storage.netty;

import java.io.File;
import java.util.Timer;

public class Program {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		//HttpUploadClient client = new HttpUploadClient("account name","account key");
		HTTPUploadClientAsync client2 = new HTTPUploadClientAsync("reuben","zGXjHNLJEnvB3uUAEEIHY7C3I0q4e2UzYwV8P4PhPgdrjenu8YqkUf47OyB9xI9wK5DRZfn3xs4KzoyoHxvnTg==");

		try {
			//File[] files = new File("C:\\test").listFiles();
			
			long start = System.currentTimeMillis();
			
			//for (File file : files) {
				System.out.println("Putting blob: " + args[0]);
				client2.putBlob(args[0], args[1]);
			//}
			
			long stop = System.currentTimeMillis();
			System.out.println((stop - start)/1000 + "seconds");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
