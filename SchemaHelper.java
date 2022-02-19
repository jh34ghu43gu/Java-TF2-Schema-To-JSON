import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import ch.qos.logback.classic.Logger;

/**
 * A class to deal with the tf2 item schema.
 * @author jh34ghu43gu
 */
public class SchemaHelper {
	
	private static final Logger log = (Logger) LoggerFactory.getLogger(SchemaHelper.class);
	private static String TEMP_FILE_NAME = "schemaTemp";
	
	/**
	 * Turn the tf2 schema into a .json readable file.
	 * @param charSet	CharSet to use for decoding/encoding. Schema uses UTF8 and tf_english uses UTF16
	 * @param filename	Schema file name
	 * @param newFileName	The output json file name including .json extension
	 */
	public static void fixSchema(Charset charSet, String filename, String newFileName) {
		log.debug("Attempting to fix schema file " + filename);
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		try {
			InputStream is = new FileInputStream(filename);
			JsonObject obj = gson.fromJson(new InputStreamReader(is, charSet), JsonObject.class);
			obj.toString();
			log.debug("Can already read schema.");
		} catch(Exception e) {
			log.debug(e.getMessage());
			log.debug("Could not read schema, applying fix...");
			File temp = null;
			try {
				//I hate this very much
				
				String old = new String(Files.readAllBytes(Paths.get(filename)), charSet);
				
				
				old = old.replaceAll("\\\\", "/"); //Replace escape characters in file paths etc..
				old = "{\n" + old.replaceAll("\t", " "); //Replace all the tabs with spaces and add opening {
				old = old.replaceAll("(  +)", " ") + "}"; //Condense all spaces into a single space 
				old = old.replaceAll("\" \"", "\":\""); //Put the : in between attributes 
				old = old.replaceAll("([^:/])\"\"", "$1\":\""); //Continue : for attributes
				//Remove comments (tf_english.txt)
				old = old.replaceAll("https://", "HTTPS"); //Temporary cache https:// for next step
				old = old.replaceAll("//.*", " "); //Remove any comments
				old = old.replaceAll("HTTPS", "https://"); //Restore https://
				//Un-replace escape characters on quotes
				old = old.replaceAll("\\.com/\"", "DOTCOM"); //Temporary cache .com/" for next step
				old = old.replaceAll("/\"", "\\\\\""); //Replace /" with \"
				old = old.replaceAll("DOTCOM", "\\.com/\""); //Restore .com/"
				
				//This is because regex hates me and cannot tell if something comes after a newline "\"\n *\\{"
				String noNewLines = old.replaceAll(System.getProperty("line.separator"), "NEWLINE");
				noNewLines = noNewLines.replaceAll(" *NEWLINE( *NEWLINE)+", "NEWLINE");
				noNewLines = noNewLines.replaceAll(" +NEWLINE", "NEWLINE");
				noNewLines = noNewLines.replaceAll("\"NEWLINE *\\{", "\":NEWLINE{");
				noNewLines = noNewLines.replaceAll("\" *NEWLINE *\"", "\",NEWLINE\"");
				noNewLines = noNewLines.replaceAll("\\} *NEWLINE *(\"[a-zA-Z0-9_ \\-:\\.]*\":)", "},NEWLINE $1"); //Fix closing brackets
				
				String revertNEWLINE = noNewLines.replaceAll("NEWLINE", "\n");
				
				
				//Convert into a temp file, if anything goes wrong in future schema conversions it can be troubleshooted.
				temp = File.createTempFile(TEMP_FILE_NAME, ".json");
				log.debug("Created temp file: " + temp.toString());
				BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
				bw.write(revertNEWLINE);
				bw.close();
				
				//Convert back into a pretty json file if it's good.
				FileReader reader = new FileReader(temp);
				JsonObject obj = gson.fromJson(reader, JsonObject.class);
				String s = gson.toJson(obj);
				FileWriter writer = new FileWriter(newFileName);
				writer.write(s);
				writer.flush();
				writer.close();
				reader.close();
				
				log.debug("Finished converting " + filename + " into the json file: " + newFileName);
			} catch(Exception e2) {
				log.error("Something else went wrong applying the fix");
				log.error(e2.getMessage());
			} finally {
				if(!temp.delete()) {
					log.warn("Failed to delete temp file");
				}
			}
		}
	}
}
