import numpy as np
import tensorflow as tf
import librosa
from os.path import join
import os
from java import jclass
from os.path import join, dirname
def chunk(incoming, n_chunk):
    input_length = incoming.shape[1]
    chunk_length = input_length // n_chunk
    outputs = []
    for i in range(incoming.shape[0]):
        for j in range(n_chunk):
            outputs.append(incoming[i, j*chunk_length:(j+1)*chunk_length, :])
    outputs = np.array(outputs)
    return outputs

def audio_read(f):
    y, sr = librosa.core.load(f, sr=22050)
    d = librosa.core.get_duration(y=y, sr=sr)
    S = librosa.feature.melspectrogram(y=y, sr=sr, n_fft=2048, hop_length=512, n_mels=128)
    S = np.transpose(np.log(1+10000*S))
    S = np.expand_dims(S, axis=0)
    return y, S, int(d)

def positional_encoding(batch_size, n_pos, d_pos):
    position_enc = np.array([
        [pos / np.power(10000, 2 * (j // 2) / d_pos) for j in range(d_pos)]
        if pos != 0 else np.zeros(d_pos) for pos in range(n_pos)])
    position_enc[1:, 0::2] = np.sin(position_enc[1:, 0::2])
    position_enc[1:, 1::2] = np.cos(position_enc[1:, 1::2])
    position_enc = np.tile(position_enc, [batch_size, 1, 1])
    return position_enc

def extract_tflite(f, length=30):
    model_path = join(dirname(__file__), "model", "music_highlighter.tflite")
    f = join(dirname(__file__), "/data/user/0/com.example.test/files/python/model/", "temp1.wav")
    # Tải mô hình TFLite
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    audio, spectrogram, duration = audio_read(f)
    n_chunk, remainder = np.divmod(duration, 3)
    chunk_spec = chunk(spectrogram, n_chunk)
    pos = positional_encoding(batch_size=1, n_pos=n_chunk, d_pos=256)

    # Chuyển đổi dữ liệu đầu vào sang kiểu float32
    n_chunk = np.array(n_chunk, dtype=np.int32)
    chunk_spec = chunk_spec.astype(np.float32)
    pos = pos.astype(np.float32)

    # Resize tensor input nếu cần thiết
    interpreter.resize_tensor_input(input_details[0]['index'], pos.shape)
    interpreter.resize_tensor_input(input_details[2]['index'], chunk_spec.shape)
    interpreter.allocate_tensors()

    interpreter.set_tensor(input_details[0]['index'], pos)
    interpreter.set_tensor(input_details[1]['index'], n_chunk)
    interpreter.set_tensor(input_details[2]['index'], chunk_spec)

    interpreter.invoke()

    attn_score = interpreter.get_tensor(output_details[0]['index'])
    attn_score = np.repeat(attn_score, 3)
    attn_score = np.append(attn_score, np.zeros(remainder))

    attn_score = attn_score / attn_score.max()
    attn_score = attn_score.cumsum()
    attn_score = np.append(attn_score[length], attn_score[length:] - attn_score[:-length])
    index = np.argmax(attn_score)
    highlight = [index, index + length]
    print(highlight)
    return highlight
