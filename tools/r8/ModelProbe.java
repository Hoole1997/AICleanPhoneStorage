import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.docview.push.config.ConfigKt;
import io.docview.push.config.Content;
import io.docview.push.config.ContentKt;
import io.docview.push.earthquake.EarthquakeInfo;
import io.docview.push.earthquake.EarthquakeResponse;
import net.corekit.metrics.revenue.RevenueConfigItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 独立 R8 宿主：执行真实模型/解析函数，不引入广告 SDK 的宽泛 keep 来掩盖问题。 */
public final class ModelProbe {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Gson gson = new Gson();
        var config = ConfigKt.parseNotificationConfig(Files.readString(
                root.resolve("notification/src/main/assets/pvvvush_config.json")));
        require(config.getPaidChannel().getDoNotDisturbStart() != null, "nested config");
        require(config.getOrganicChannel().getTotalPushCount() >= 0, "config fields");
        List<Content> contents = ContentKt.parsePushContents(Files.readString(
                root.resolve("notification/src/main/assets/pvvvvush_content_config.json")));
        require(!contents.isEmpty() && !contents.get(0).getTitle().isEmpty(), "TypeToken content list");
        require(gson.toJsonTree(contents.get(0)).getAsJsonObject().has("actionType"), "content JSON key");
        for (String file : List.of("revenue_config.json", "firebase_revenue_config.json")) {
            RevenueConfigItem[] revenue = gson.fromJson(Files.readString(
                    root.resolve("metrics/src/main/assets/" + file)), RevenueConfigItem[].class);
            require(revenue.length > 0 && !revenue[0].getName().isEmpty(), "revenue array");
            require(revenue[0].getRate() > 0, "revenue numeric field");
            require(gson.toJsonTree(revenue[0]).getAsJsonObject().has("rate"), "revenue JSON key");
        }
        // 包括只能通过泛型字段发现的嵌套 DTO，检查 R8 未移除签名或可实例化性。
        EarthquakeResponse earthquake = gson.fromJson("""
                {"type":"FeatureCollection","metadata":{"generated":123,"url":"u","title":"t","status":200,"api":"1","count":1},
                 "features":[{"type":"Feature","id":"id","properties":{"mag":3.5,"place":"p","time":123,"tsunami":0},
                 "geometry":{"type":"Point","coordinates":[1.0,2.0,3.0]}}]}
                """, EarthquakeResponse.class);
        require(earthquake.getMetadata().getCount() == 1, "earthquake metadata");
        require(earthquake.getFeatures().get(0).getProperties().getMag() == 3.5, "generic model element");
        require(earthquake.getFeatures().get(0).getGeometry().getCoordinates().get(2) == 3.0, "generic scalar element");
        EarthquakeInfo info = new EarthquakeInfo(3.5, "time", 3.0, false,
                "green", "ml", "reviewed", "place", "short", "ML");
        var json = JsonParser.parseString(gson.toJson(info)).getAsJsonObject();
        require(json.size() == 10 && json.get("magnitude").getAsDouble() == 3.5, "earthquake serialization");
        require(json.get("shortMagType").getAsString().equals("ML"), "earthquake JSON key");
        System.out.println("PASS: config, content TypeToken, both revenue arrays, nested earthquake models, JSON keys");
    }

    private static void require(boolean condition, String contract) {
        if (!condition) throw new AssertionError(contract);
    }
}
