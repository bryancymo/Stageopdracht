package com.stage.adapter.mvb;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.stage.adapter.mvb.producers.Catalog;
import com.stage.adapter.mvb.producers.CurrentData;
import com.stage.adapter.mvb.streams.KitableWindStream;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

// doc: https://docs.confluent.io/5.4.1/schema-registry/schema_registry_tutorial.html
// ⚠️

@SpringBootApplication
public class Application {

	private static String API = "https://api.meetnetvlaamsebanken.be";

//	private static final String[] sensoren = {"A2BGHA", "WDLGHA", "RA2GHA", "OSNGHA", "NPBGHA", "SWIGHA",
//			"MP0WC3", "MP7WC3", "NP7WC3", "MP0WVC", "MP7WVC", "NP7WVC", "A2BRHF", "RA2RHF", "OSNRHF"};
	
	
	private static final Logger logger = LogManager.getLogger(Application.class);
	
	
	public static void main(String[] args) {
//		SpringApplication.run(Application.class, args);
		
		Configurator.initialize(null, "src/main/resources/log4j2.xml");
		
		CurrentData currentData = new CurrentData(API);
		Catalog catalog = new Catalog(API);
		KitableWindStream kitableWindStream = new KitableWindStream(getProperties());
		
		Thread currentDataThread = new Thread(currentData);
		Thread catalogThread = new Thread(catalog);
		Thread windStreamThread = new Thread(kitableWindStream);
		
		currentDataThread.start();
		catalogThread.start();
		windStreamThread.start();
		
		
		while(currentData.getCurrentDataString() == null || catalog.getCatalogString() == null) {
			if(currentData.getCurrentDataString() == null) {
				logger.info("ℹ️ retrieving current data" );
			}
			if(catalog.getCatalogString() == null) {
				logger.info("ℹ️ Retrieving catalog");
			}
		
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error("❌ ", e);
			}
			
		};
		
		ApplicationHelper applicationHelper = new ApplicationHelper(currentData, catalog);
		Thread applicationHelperThread = new Thread(applicationHelper);
		applicationHelperThread.start();
	}
	
	private static Properties getProperties() {
		
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 0);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "MVB_consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
//        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        
        return props;
	}
	
	private static String[] getParams(JSONObject cat, JSONObject currentData, String sensor) {
		String loc = null;
		String eenheid = null;
		long millis= 0L;
		String value = null;
		JSONArray locations = cat.getJSONArray("Locations");
		for (int i = 0; i < locations.length(); i++) {
            JSONObject location = locations.getJSONObject(i);
            if (location.getString("ID").equals(sensor.substring(0,sensor.length() - 3))) {
                JSONArray names = location.getJSONArray("Name");
                JSONObject name = names.getJSONObject(0);
                loc = name.getString("Message");
                break;
            }
        }
		JSONArray parameters = cat.getJSONArray("Parameters");

		for (int i = 0; i < parameters.length(); i++) {
		    JSONObject parameter = parameters.getJSONObject(i);
		    String id = parameter.getString("ID");
		    if (id.equals(sensor.substring(sensor.length()-3))) {
		        eenheid = parameter.getString("Unit");
		        break;
		    }
		}
		
		JSONArray data = currentData.getJSONArray("current data");
		for (int i = 0; i < data.length(); i++) {
			JSONObject sensorData = data.getJSONObject(i);
			if (sensorData.getString("ID").equals(sensor)) {
				try {
					SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
					String timestamp = sensorData.getString("Timestamp");
					String dateString = timestamp.split("T")[0];
					String timeZoned = timestamp.split("T")[1];
					String timeString = timeZoned.substring(0, timeZoned.length() - 6);
					Date date;
					date = sdf.parse(String.format("%s %s", dateString, timeString));
					millis = date.getTime();
				} catch (ParseException e) {
					e.printStackTrace();
					
				}
				
				value = String.valueOf(sensorData.getFloat("Value"));
				break;
			}
		}
		
		return new String[] {loc, value, eenheid, Long.toString(millis)};
	}

}
