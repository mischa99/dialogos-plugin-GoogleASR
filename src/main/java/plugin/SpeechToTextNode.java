package plugin;

import com.clt.diamant.Device;
import com.clt.diamant.IdMap;
import com.clt.diamant.InputCenter;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.script.exp.patterns.VarPattern;
import com.clt.speech.Language;
import com.clt.speech.recognition.LanguageName;
import com.clt.srgf.Grammar;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.xml.sax.SAXException;

import javax.sound.sampled.AudioFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
//import org.jsoup.Jsoup;



/**
 * Created by Mikhail on 17.02.20.
 */
public class SpeechToTextNode extends AbstractGoogleNode {

    String lang_name = "";
    String language = "";

    public SpeechToTextNode() {
        //TODO: load JComboBox language value from opened file, then initialize AbstractGoogleNode.SELECTED_LANGUAGE with it
        AbstractGoogleNode.SELECTED_LANGUAGE= new LanguageName(lang_name,new Language(language));
        System.out.println("MR" + lang_name + " ___ " +language);
    }



    @Override
    public GoogleRecognitionExecutor createRecognitionExecutor(Grammar recGrammar) {

         recGrammar.requestRobustness(Boolean.TRUE == getProperty(ENABLE_GARBAGE));
         return new GoogleRecognitionExecutor();
        //return null;
    }


    @Override
    public Device getDevice() {
        return null;
    }


    @Override
    public AudioFormat getAudioFormat() {
        // return null;
        return new AudioFormat(16000, 16, 1, true, false);
    }

    @Override
    public void recognizeInBackground(Grammar recGrammar, InputCenter input, VarPattern backgroundPattern, float confidenceThreshold) {
        throw new NodeExecutionException(this, "TextInputNode does not support background recognition");
    }

    protected static LanguageName defaultLanguage = new LanguageName("US English", new Language(Locale.US));
    //private LanguageName defaultLanguage1 = new LanguageName("", null);

    @Override
    public List<LanguageName> getAvailableLanguages() {
        ArrayList<LanguageName> list = new ArrayList<LanguageName>();
        LanguageName d = new LanguageName("Deutsch", new Language(Locale.GERMANY));
        list.add(d);
        list.add(new LanguageName("US English", new Language(Locale.US)));
        list.add(new LanguageName("Français", new Language(Locale.FRANCE)));
        list.add(new LanguageName("Español", new Language(new Locale("es", "ES"))));
        return list;
    }

    @Override
    public LanguageName getDefaultLanguage() {
        return defaultLanguage;
    }

    @Override
    public void writeAttributes(XMLWriter out, IdMap uid_map) {
        super.writeAttributes(out, uid_map); //***saves graph information to file
        /*
        Slot v = (Slot) this.getProperty(RESULT_VAR);
        if (v != null) {
            try {
                String uid = uid_map.variables.getKey(v);
                Graph.printAtt(out, RESULT_VAR, uid);
            } catch (Exception exn) {
            } // variable deleted
        }
         */
        Graph.printAtt(out, String.valueOf(SELECTED_LANGUAGE_NAME), AbstractGoogleNode.SELECTED_LANGUAGE_NAME); //save result_var in the file
    Graph.printAtt(out,"LanguageName", String.valueOf(SELECTED_LANGUAGE), AbstractGoogleNode.SELECTED_LANGUAGE.getLanguage());

    }



    @Override
    public void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(SELECTED_LANGUAGE_NAME) && value!=null) {
            //try {
            this.setProperty(name, value);
            lang_name = value;
            // uid_map.variables.get(value) instead of value
            //} catch (Exception exn) {
            //    r.raiseException(com.clt.diamant.Resources.format("UnknownVariable", "ID " + value));
            // }
        }
        else if(name.equals(SELECTED_LANGUAGE) && value != null) {
            this.setProperty(name, value);
            language = value;
        }
        else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
    public ArrayList<String> getGoogleLanguages() {
        return null;
    }

    /**
     * Once there is a Class for supported languages in the API this might be used
     *
    public ArrayList<String> getGoogleLanguages() throws IllegalAccessException {
        ArrayList<String> list = new ArrayList<String>();
        Class[] languageCodes = LanguageCodes.class.getDeclaredClasses(); //get all inner classes of LanguageCode
        for(Class innerClass: languageCodes){
            System.out.println(innerClass.getName());
            Field[] fields = innerClass.getDeclaredFields(); //get all Fields of inner class (should be just one)
            assert (fields.length==1);
            System.out.println(fields[0].getName());
            try {
                list.add(fields[0].get(innerClass).toString()); //gets first field of innerClass
            }
            catch(Exception e){

            }
        }

        return list;
    }
     **/
        /**
         * try to get the languages from the docs

        org.jsoup.nodes.Document doc = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            doc = Jsoup.connect("https://cloud.google.com/speech-to-text/docs/languages").get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        org.jsoup.select.Elements rows = doc.select("tr");
        for(org.jsoup.nodes.Element row :rows)
        {
            org.jsoup.select.Elements columns = row.select("td");
            for (org.jsoup.nodes.Element column:columns)
            {
                System.out.print(column.text());
              //  list.add(new LanguageName("", column.text()));
            }
            System.out.println();
        }
        return list;

         **/

}




