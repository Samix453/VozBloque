package com.samix.vozbloque.client;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** Enlace directo (JNA) a la librería nativa de Vosk. */
public interface VoskLib extends Library {
	void vosk_set_log_level(int level);

	Pointer vosk_model_new(String path);

	void vosk_model_free(Pointer model);

	Pointer vosk_recognizer_new(Pointer model, float sampleRate);

	/** grammar: JSON en UTF-8 terminado en \0 */
	Pointer vosk_recognizer_new_grm(Pointer model, float sampleRate, byte[] grammar);

	int vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int length);

	Pointer vosk_recognizer_result(Pointer recognizer);

	Pointer vosk_recognizer_partial_result(Pointer recognizer);

	Pointer vosk_recognizer_final_result(Pointer recognizer);

	void vosk_recognizer_reset(Pointer recognizer);

	void vosk_recognizer_free(Pointer recognizer);
}
