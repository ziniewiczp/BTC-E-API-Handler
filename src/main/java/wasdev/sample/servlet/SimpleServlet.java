package wasdev.sample.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet implementation class SimpleServlet
 */
@WebServlet("/SimpleServlet")
public class SimpleServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	
	private String responseString = "<table><tr><th>Timestamp:</th><th>Ask price:</th><th>Ask size:</th><th>Bid price:</th><th>Bid size:</th></tr>";
	
	public void init()
	{
		MyThread myThread = new MyThread();
		myThread.start();
	}
	
	public class MyThread extends Thread
	{
	    public void run() 
	    {   
	    	while(true) 
	    	{	
		    	try 
		    	{
					URL url = new URL("https://btc-e.com/api/3/depth/btc_usd?limit=5/");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setRequestProperty("Accept", "application/json");
	
					if (conn.getResponseCode() != 200)
					{
						throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
					}
	
					BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
	
					String output;
	
					while ((output = br.readLine()) != null)
					{
						// System.currentTimeMillis() provides current time in
						// milliseconds since the UNIX epoch (Jan 1, 1970).
						// Dividing is used to get time in seconds.
						long unixTime = System.currentTimeMillis() / 1000L;
	
						JSONObject obj = new JSONObject(output);
						JSONArray asksArray = obj.getJSONObject("btc_usd").getJSONArray("asks");
						JSONArray bidsArray = obj.getJSONObject("btc_usd").getJSONArray("bids");
	
						System.out.println(Thread.currentThread().getName());
						System.out.println("Timestamp: " + unixTime);
						System.out.println("Ask price: " + asksArray.getJSONArray(0).getBigDecimal(0));
						System.out.println("Ask size: " + asksArray.getJSONArray(0).getBigDecimal(1));
						System.out.println("Bid price: " + bidsArray.getJSONArray(0).getBigDecimal(0));
						System.out.println("Bid size: " + bidsArray.getJSONArray(0).getBigDecimal(1));
						System.out.println("\n");
						
						responseString = responseString + "<tr><td>" + unixTime + "</td>" 
								+ "<td>" + asksArray.getJSONArray(0).getBigDecimal(0) + "</td>" 
								+ "<td>" + asksArray.getJSONArray(0).getBigDecimal(1) + "</td>"
								+ "<td>" + bidsArray.getJSONArray(0).getBigDecimal(0) + "</td>"
								+ "<td>" + bidsArray.getJSONArray(0).getBigDecimal(1) + "</td>";
					}
	
					output = "";
	
					conn.disconnect();
					
					Thread.sleep(2000);
	
				} catch (MalformedURLException e)
		    	{
					e.printStackTrace();
					
				} catch (IOException e)
		    	{
					e.printStackTrace();
					
				} catch (InterruptedException e)
		    	{
					e.printStackTrace();
				}
	    	}
	    }
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException
	{	
		response.setContentType("text/html");
		response.getWriter().print(responseString + "</tr></table>");
	}
}
