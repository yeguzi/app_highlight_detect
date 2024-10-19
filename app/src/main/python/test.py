import numpy as np
import librosa
from os.path import join, dirname
import os
import tflite_runtime.interpreter as tflite  # Sử dụng tflite_runtime

interpreter = None

def load_model():
    global interpreter
    model_path = join(dirname(__file__), "model", "music_highlighter.tflite")
    interpreter = tflite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    print("Model loaded successfully")

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
    S = np.transpose(np.log(1 + 10000 * S))
    S = np.expand_dims(S, axis=0)
    return y, S, int(d)

def positional_encoding(batch_size, n_pos, d_pos):
    position_enc = np.array([
        [pos / np.power(10000, 2 * (j // 2) / d_pos) for j in range(d_pos)]
        if pos != 0 else np.zeros(d_pos) for pos in range(n_pos)
    ])
    position_enc[1:, 0::2] = np.sin(position_enc[1:, 0::2])
    position_enc[1:, 1::2] = np.cos(position_enc[1:, 1::2])
    position_enc = np.tile(position_enc, [batch_size, 1, 1])
    return position_enc

def extract_tflite(f, length=15):
    if interpreter is None:
        raise RuntimeError("Model is not loaded. Call load_model() first.")

    f = os.path.join(os.path.dirname(__file__), "model", "temp.wav")

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
    interpreter.resize_tensor_input(input_details[1]['index'], n_chunk.shape)
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
#     index = np.argmax(attn_score)
#     highlight = [index, index + length]
#     print(highlight)
    sorted_indices = np.argsort(attn_score)[::-1]
    predictions = []
    used_indices = set()

    for index in sorted_indices:
        if len(predictions) == 3:
            break

        is_overlap = any(index < h_end and index + length > h_start for (h_start, h_end) in predictions)

        if not is_overlap:
             predictions.append([index, index + length])

    highlight = [h_start for h_start, h_end in predictions]
    print(highlight)
    return highlight

def process(content):

    content = bytes(content)

    model_dir = os.path.join(os.path.dirname(__file__), "model")
    os.makedirs(model_dir, exist_ok=True)

    filename = os.path.join(model_dir, "temp.wav")

    # Ghi nội dung vào tệp
    with open(filename, "wb") as temp_file:
        temp_file.write(content)

    return filename
