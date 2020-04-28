package client;

import com.clt.properties.Property;
import com.clt.speech.Language;
import com.clt.speech.SpeechException;
import edu.cmu.lti.dialogos.sphinx.client.SingleDomainRecognizer;

import javax.sound.sampled.AudioFormat;

public abstract class GoogleBaseRecognizer extends SingleDomainRecognizer {
    public static final AudioFormat audioFormat = new AudioFormat(16000f, 16, 1, true, false);
    public static AudioFormat getAudioFormat() { return audioFormat; }

    public Property<?>[] getProperties() {
        return new Property<?>[0];
    }

    /** only ever called from TranscriptionWindow (and nobody seems to use that */
    @Override public String[] transcribe(String word, Language language) throws SpeechException {
        return new String[]{};
    }
}
