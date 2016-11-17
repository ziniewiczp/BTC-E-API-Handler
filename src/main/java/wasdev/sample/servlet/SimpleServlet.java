package wasdev.sample.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
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

import java.sql.*;

/**
 * Servlet implementation class SimpleServlet
 */
@WebServlet("/SimpleServlet")
public class SimpleServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public void init() {
		
		MyThread myThread = new MyThread();
		myThread.start();
	}

	public class MyThread extends Thread {
		public void run() {
			
			Connection dbConnection = null;
			Statement dbStatement = null;
			BigDecimal askPrice, askSize, bidPrice, bidSize;
			long unixTime;
			
			while (true) {
				
				try {
					
					// registering JDBC driver
					Class.forName("com.ibm.db2.jcc.DB2Driver");
					
					// creating DB connection
					dbConnection = DriverManager.getConnection("jdbc:db2://dashdb-entry-yp-dal09-10.services.dal.bluemix.net:50000/"
							+ "BLUDB:user=dash7003;password=aajg39FWEYxj;");
					
					URL url = new URL("https://btc-e.com/api/3/depth/btc_usd?limit=5/");
					HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
					urlConnection.setRequestMethod("GET");
					urlConnection.setRequestProperty("Accept", "application/json");

					if (urlConnection.getResponseCode() != 200) {
						throw new RuntimeException("Failed : HTTP error code : " + urlConnection.getResponseCode());
					}

					BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getInputStream())));

					String output;

					while ((output = br.readLine()) != null) {
						// System.currentTimeMillis() provides current time in
						// milliseconds since the UNIX epoch (Jan 1, 1970).
						// Dividing is used to get time in seconds.
						unixTime = System.currentTimeMillis() / 1000L;

						JSONObject obj = new JSONObject(output);
						JSONArray asksArray = obj.getJSONObject("btc_usd").getJSONArray("asks");
						JSONArray bidsArray = obj.getJSONObject("btc_usd").getJSONArray("bids");
						
						askPrice = asksArray.getJSONArray(0).getBigDecimal(0);
						askSize = asksArray.getJSONArray(0).getBigDecimal(1);
						bidSize = bidsArray.getJSONArray(0).getBigDecimal(1);
						bidPrice = bidsArray.getJSONArray(0).getBigDecimal(0);

						System.out.println("Timestamp: " + unixTime);
						System.out.println("Ask price: " + askPrice);
						System.out.println("Ask size: " + askSize);
						System.out.println("Bid price: " + bidPrice);
						System.out.println("Bid size: " + bidSize);
						System.out.println("\n");
						
						// inserting latest data to database
						dbStatement = dbConnection.createStatement();
					    dbStatement.executeUpdate("INSERT INTO BTCETable VALUES ("
					    		+ unixTime + "," + askSize + "," + askPrice + "," + bidPrice + "," + bidSize + ")" );
					}
					
					output = "";
					urlConnection.disconnect();
					
				    dbStatement.close();
				    dbConnection.close();

					Thread.sleep(2000);

				} catch (MalformedURLException e) {
					e.printStackTrace();

				} catch (IOException e) {
					e.printStackTrace();

				} catch (InterruptedException e) {
					e.printStackTrace();
					
				} catch (SQLException e) {
					e.printStackTrace();
					
				} catch (ClassNotFoundException e) {

					e.printStackTrace();
				} finally {
					try {
				         if(dbStatement != null)
				            dbStatement.close();
				         
				    } catch(SQLException e){
				    	  e.printStackTrace();
				    }
					
					try {
				         if(dbConnection != null)
				            dbConnection.close();
				         
				    } catch(SQLException e){
				    	  e.printStackTrace();
				    }
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
			throws ServletException, IOException {
		
		Connection dbConnection = null;
		Statement dbStatement = null;
		ResultSet resultSet = null;
		
		String responseString = "<table><tr><th>Timestamp:</th><th>Ask size:</th><th>Ask price:</th><th>Bid price:</th><th>Bid size:</th></tr>";
		
		response.setContentType("text/html");
		
		try {
			// registering JDBC driver
			Class.forName("com.ibm.db2.jcc.DB2Driver");
			
			// creating DB connection
			dbConnection = DriverManager.getConnection("jdbc:db2://dashdb-entry-yp-dal09-10.services.dal.bluemix.net:50000/"
					+ "BLUDB:user=dash7003;password=aajg39FWEYxj;");
		
			dbStatement = dbConnection.createStatement();
		    resultSet = dbStatement.executeQuery("SELECT timestamp, ask_size, ask_price, bid_price, bid_size FROM BTCETable LIMIT 100");
		    
		    while(resultSet.next()) {
		    	
		    	responseString = responseString + "<tr><td>" + resultSet.getLong("timestamp") + "</td>"
		    			+ "<td>" + resultSet.getBigDecimal("ask_size") + "</td>"
		    			+ "<td>" + resultSet.getBigDecimal("ask_price") + "</td>"
		    			+ "<td>" + resultSet.getBigDecimal("bid_price") + "</td>"
		    			+ "<td>" + resultSet.getBigDecimal("bid_size") + "</td>";
		    }
		    
		    resultSet.close();
		    dbStatement.close();

		} catch (SQLException e) {
			e.printStackTrace();
			
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
		         if(dbStatement != null)
		            dbStatement.close();
		         
		    } catch(SQLException e){
		    	  e.printStackTrace();
		    }
			
			try {
		         if(dbConnection != null)
		            dbConnection.close();
		         
		    } catch(SQLException e){
		    	  e.printStackTrace();
		    }
		}

		response.getWriter().print(responseString + "</tr></table>");
	}
}
