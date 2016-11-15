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
public class SimpleServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// Set refresh, autoload time as 5 seconds
		response.setIntHeader("Refresh", 2);

		System.out.println("Output from Server .... \n");

		try {

			URL url = new URL("https://btc-e.com/api/3/depth/btc_usd/");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			while ((output = br.readLine()) != null) {
				// System.out.println(output);

				// System.currentTimeMillis() provides current time in
				// milliseconds since the UNIX epoch (Jan 1, 1970).
				// Dividing is used to get time in seconds.
				long unixTime = System.currentTimeMillis() / 1000L;

				JSONObject obj = new JSONObject(output);
				JSONArray asksArray = obj.getJSONObject("btc_usd").getJSONArray("asks");
				JSONArray bidsArray = obj.getJSONObject("btc_usd").getJSONArray("bids");

				System.out.println("Timestamp: " + unixTime);
				System.out.println("Ask price: " + asksArray.getJSONArray(0).getBigDecimal(0));
				System.out.println("Ask size: " + asksArray.getJSONArray(0).getBigDecimal(1));
				System.out.println("Bid price: " + bidsArray.getJSONArray(0).getBigDecimal(0));
				System.out.println("Bid size: " + bidsArray.getJSONArray(0).getBigDecimal(1));
				System.out.println("\n");
				
				response.setContentType("text/html");
				response.getWriter().print(unixTime + " " 
						+ asksArray.getJSONArray(0).getBigDecimal(0) + " " 
						+ asksArray.getJSONArray(0).getBigDecimal(1) + " "
						+ bidsArray.getJSONArray(0).getBigDecimal(0) + " "
						+ bidsArray.getJSONArray(0).getBigDecimal(1)
						);
			}

			output = "";
			//Thread.sleep(2000);

			conn.disconnect();

		} catch (MalformedURLException e) {

			e.printStackTrace();

		} catch (IOException e) {

			e.printStackTrace();
		}
	}
}
