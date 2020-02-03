import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class BalzacRequest {
    public static void main(String[] args) throws  Exception {
        String code = "fullText="+URLEncoder.encode(args[0], StandardCharsets.UTF_8);;
        code = code.replace("%20", "+");
        code = code.replace("%2A", "*");
        code = code.replace("%27", "'");
        String jsess = putCode(code);
        String out = getCode(jsess);
        System.out.println(out);
    }

    private static String putCode(String code) throws Exception {
        URL url = new URL("https://blockchain.unica.it/balzac/xtext-service/update?resource=1a06317.balzac");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        conn.setRequestProperty( "Accept", "*/*" );
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);

        OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
        osw.write(code);
        osw.flush();
        osw.close();

        String result = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                .lines().collect(Collectors.joining("\n"));

        String jsess = conn.getHeaderField("Set-cookie").substring(0, 43);
        conn.disconnect();

        return jsess;
    }

    private static String getCode(String jsess) throws Exception {
        URL url2 = new URL("https://blockchain.unica.it/balzac/xtext-service/generate?resource=1a06317.balzac&requiredStateId=-80000000");
        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
        conn2.setRequestProperty("Cookie", jsess);
        conn2.setRequestMethod("GET");
        String result2 = new BufferedReader(new InputStreamReader(conn2.getInputStream()))
                .lines().collect(Collectors.joining("\n"));

        return result2;
    }

}

