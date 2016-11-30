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
			ResultSet resultSet = null;
			BigDecimal askPrice, askSize, bidPrice, bidSize;

			// initialized with system-based method just in case
			long timestamp = System.currentTimeMillis() / 1000L;
			long latestTimestamp;

			while (true) {

				try {

					// registering JDBC driver
					Class.forName("com.ibm.db2.jcc.DB2Driver");

					// creating DB connection
					dbConnection = DriverManager.getConnection(
							"jdbc:db2://dashdb-entry-yp-dal09-10.services.dal.bluemix.net:50001/BLUDB:user=dash7003;"
									+ "password=aajg39FWEYxj;sslConnection=true;");

					/**
					 * sending BTC-E API response to acquire it's current server
					 * time (timestamp)
					 */
					URL infoUrl = new URL("https://btc-e.com/api/3/info");
					HttpURLConnection infoUrlConnection = (HttpURLConnection) infoUrl.openConnection();
					infoUrlConnection.setRequestMethod("GET");
					infoUrlConnection.setRequestProperty("Accept", "application/json");

					if (infoUrlConnection.getResponseCode() != 200) {
						throw new RuntimeException("Failed : HTTP error code : " + infoUrlConnection.getResponseCode());
					}

					BufferedReader infoBufferedReader = new BufferedReader(
							new InputStreamReader((infoUrlConnection.getInputStream())));

					String infoOutput;

					while ((infoOutput = infoBufferedReader.readLine()) != null) {

						JSONObject infoObj = new JSONObject(infoOutput);

						timestamp = infoObj.getLong("server_time");
					}

					infoOutput = "";

					/**
					 * sending BTC-E API request to get info about active orders
					 * on the pair (depth)
					 */
					URL depthUrl = new URL("https://btc-e.com/api/3/depth/btc_usd?limit=1/");
					HttpURLConnection depthUrlConnection = (HttpURLConnection) depthUrl.openConnection();
					depthUrlConnection.setRequestMethod("GET");
					depthUrlConnection.setRequestProperty("Accept", "application/json");

					if (depthUrlConnection.getResponseCode() != 200) {
						throw new RuntimeException(
								"Failed : HTTP error code : " + depthUrlConnection.getResponseCode());
					}

					BufferedReader depthBufferedReader = new BufferedReader(
							new InputStreamReader((depthUrlConnection.getInputStream())));

					String depthOutput;

					while ((depthOutput = depthBufferedReader.readLine()) != null) {

						JSONObject obj = new JSONObject(depthOutput);
						JSONArray asksArray = obj.getJSONObject("btc_usd").getJSONArray("asks");
						JSONArray bidsArray = obj.getJSONObject("btc_usd").getJSONArray("bids");

						askPrice = asksArray.getJSONArray(0).getBigDecimal(0);
						askSize = asksArray.getJSONArray(0).getBigDecimal(1);
						bidSize = bidsArray.getJSONArray(0).getBigDecimal(1);
						bidPrice = bidsArray.getJSONArray(0).getBigDecimal(0);

						System.out.println("Timestamp: " + timestamp);
						System.out.println("Ask price: " + askPrice);
						System.out.println("Ask size: " + askSize);
						System.out.println("Bid price: " + bidPrice);
						System.out.println("Bid size: " + bidSize);
						System.out.println("\n");

						/**
						 * checking if something happened on active pairs list 
						 * since the last check
						 */
						dbStatement = dbConnection.createStatement();
						resultSet = dbStatement.executeQuery(
								"SELECT * FROM BTCETable WHERE trade_type IS NULL ORDER BY timestamp DESC LIMIT 1");

						if(resultSet.next()) {
						
							latestTimestamp = resultSet.getLong("timestamp");
	
							if ((resultSet.getBigDecimal("ask_size") != null
											&& resultSet.getBigDecimal("ask_size").compareTo(askSize) != 0) ||
									(resultSet.getBigDecimal("ask_price") != null
											&& resultSet.getBigDecimal("ask_price").compareTo(askPrice) != 0) ||
									(resultSet.getBigDecimal("bid_price") != null
											&& resultSet.getBigDecimal("bid_price").compareTo(bidPrice) != 0) ||
									(resultSet.getBigDecimal("bid_size") != null
											&& resultSet.getBigDecimal("bid_size").compareTo(bidSize) != 0)) {
	
								System.out.println("Something have changed.");
	
								/**
								 * checking information about last trades to see if
								 * any of them happened between now and the last check
								 */
								URL tradesUrl = new URL("https://btc-e.com/api/3/trades/btc_usd");
								HttpURLConnection tradesUrlConnection = (HttpURLConnection) tradesUrl.openConnection();
								tradesUrlConnection.setRequestMethod("GET");
								tradesUrlConnection.setRequestProperty("Accept", "application/json");
	
								if (tradesUrlConnection.getResponseCode() != 200) {
									throw new RuntimeException(
											"Failed : HTTP error code : " + tradesUrlConnection.getResponseCode());
								}
	
								BufferedReader tradesBufferReader = new BufferedReader(
										new InputStreamReader((tradesUrlConnection.getInputStream())));
	
								String tradesOutput;
	
								while ((tradesOutput = tradesBufferReader.readLine()) != null) {
	
									JSONObject tradesObj = new JSONObject(tradesOutput);
									JSONArray tradesArray = tradesObj.getJSONArray("btc_usd");
	
									System.out.println("latestTimestamp: " + latestTimestamp);
	
									// when application launches after longer break without below limitation
									// it will get whole list of latest trades
									if (timestamp - latestTimestamp < 10) {
										
										for(int i = 50; i >= 0; i--) {
										
											if(tradesArray.getJSONObject(i).getLong("timestamp") > latestTimestamp &&
													tradesArray.getJSONObject(i).getLong("timestamp") <= timestamp) {
												System.out.println(tradesArray.getJSONObject(i).get("timestamp"));
													
												long currentTradeTimestamp = tradesArray.getJSONObject(i).getLong("timestamp");
												BigDecimal currentTradeAmount = tradesArray.getJSONObject(i)
														.getBigDecimal("amount");
												BigDecimal currentTradePrice = tradesArray.getJSONObject(i)
														.getBigDecimal("price");
			
												// inserting trade information to database.
												// when BTC-E trade is marked as bid we mark it as ask,
												// their understanding must be different
												if (tradesArray.getJSONObject(i).getString("type").equals("bid")) {
													dbStatement.executeUpdate(
															"INSERT INTO BTCETable (timestamp, bid_size, bid_price, ask_price, ask_size, trade_type) VALUES ("
																	+ currentTradeTimestamp + ", null, null, "
																	+ currentTradePrice + "," + currentTradeAmount
																	+ ", 'ask' )");
												} else if (tradesArray.getJSONObject(i).getString("type").equals("ask")) {
													dbStatement.executeUpdate(
															"INSERT INTO BTCETable (timestamp, bid_size, bid_price, ask_price, ask_size, trade_type) VALUES ("
																	+ currentTradeTimestamp + "," + currentTradeAmount + ","
																	+ currentTradePrice + ", null, null, 'bid')");
												}
											}
										}
									}
								}
	
								tradesOutput = "";
								tradesUrlConnection.disconnect();
							}
						}
						
						// inserting latest data to database
						dbStatement.executeUpdate(
								"INSERT INTO BTCETable (timestamp, bid_size, bid_price, ask_price, ask_size, trade_type) VALUES ("
										+ timestamp + "," + bidSize + "," + bidPrice + "," + askPrice + "," + askSize
										+ ", null )");
					}

					System.out.println("\n");

					depthOutput = "";
					infoUrlConnection.disconnect();
					depthUrlConnection.disconnect();

					//resultSet.close();
					dbStatement.close();
					dbConnection.close();

					Thread.sleep(1000);

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
						if (dbStatement != null)
							dbStatement.close();

					} catch (SQLException e) {
						e.printStackTrace();
					}

					try {
						if (dbConnection != null)
							dbConnection.close();

					} catch (SQLException e) {
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
		String fontColor;
		String tradeType;

		String responseString = "<table><tr><th>Timestamp:</th><th>Bid size:</th><th>Bid price:</th><th>Ask price:</th><th>Ask size:</th></tr>";

		response.setContentType("text/html");

		try {
			// registering JDBC driver
			Class.forName("com.ibm.db2.jcc.DB2Driver");

			// creating DB connection
			dbConnection = DriverManager
					.getConnection("jdbc:db2://dashdb-entry-yp-dal09-10.services.dal.bluemix.net:50000/"
							+ "BLUDB:user=dash7003;password=aajg39FWEYxj;");

			dbStatement = dbConnection.createStatement();
			// nested SELECT is used to view data in proper way: last 50 rows in ascending order.
			// inner SELECT to select the correct rows, and an outer SELECT to order them correctly
			resultSet = dbStatement.executeQuery("SELECT timestamp, bid_size, bid_price, ask_price, ask_size, trade_type FROM "
					+ "(" + "SELECT * FROM BTCETable ORDER BY timestamp DESC LIMIT 100" + ")" + "ORDER BY timestamp");

			while (resultSet.next()) {
				
				tradeType = resultSet.getString("trade_type");
				
				if(resultSet.wasNull())
					fontColor = "#ffffff";
				else if(tradeType.equals("ask"))
					fontColor = "#99ccff";
				else
					fontColor = "#99ff66";

				responseString = responseString + "<tr><td><font color=\""+ fontColor + "\">" + resultSet.getLong("timestamp") + "</font></td>"
						+ "<td><font color=\""+ fontColor + "\">" + resultSet.getBigDecimal("bid_size") + "</font></td>" 
						+ "<td><font color=\""+ fontColor + "\">" + resultSet.getBigDecimal("bid_price") + "</font></td>"
						+ "<td><font color=\""+ fontColor + "\">" + resultSet.getBigDecimal("ask_price") + "</font></td>"
						+ "<td><font color=\""+ fontColor + "\">" + resultSet.getBigDecimal("ask_size") + "</font></td>";
			}

			resultSet.close();
			dbStatement.close();

		} catch (SQLException e) {
			e.printStackTrace();

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (dbStatement != null)
					dbStatement.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}

			try {
				if (dbConnection != null)
					dbConnection.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		response.getWriter().print(responseString + "</tr></table>");
	}
}
