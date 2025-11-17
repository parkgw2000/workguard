import os
import torch
import logging
import traceback
from fastapi import FastAPI, Request
from transformers import PreTrainedTokenizerFast, BartForConditionalGeneration, AutoConfig

## 파이썬 실행: uvicorn summary_api:app --reload --host 0.0.0.0 --port 8081

# 환경 변수 설정
os.environ["CUDA_VISIBLE_DEVICES"] = "0"
os.environ["TOKENIZERS_PARALLELISM"] = "false"
os.environ['TRANSFORMERS_NO_ADVISORY_WARNINGS'] = 'true'
logging.disable(logging.INFO)
logging.disable(logging.WARNING)

app = FastAPI()

model_name = 'gogamza/kobart-base-v2'
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(BASE_DIR, 'checkpoint-26606')

print("모델 경로:", model_path)
print("모델 경로 존재 여부:", os.path.exists(model_path))
print("config.json 존재 여부:", os.path.exists(os.path.join(model_path, "config.json")))

params = {
    'num_beams': 4,
    'max_length': 200,          # 반복 문제 줄이려고 1024 -> 200으로 조정
    'length_penalty': 0.5,
    'no_repeat_ngram_size': 3,  # 3-그램 반복 방지
    'early_stopping': True      # EOS 토큰 나오면 조기 종료
}

def load_model_and_tokenizer():
    print("모델 로딩 중...")
    config = AutoConfig.from_pretrained(model_path)
    model = BartForConditionalGeneration.from_pretrained(model_path, config=config)
    tokenizer = PreTrainedTokenizerFast.from_pretrained(model_name)

    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model.to(device)
    model.eval()
    print(f"모델 로딩 완료 (Device: {device})")
    return model, tokenizer, device

def preprocess_text(text: str) -> str:
    # 줄바꿈 및 특수문자 제거, 앞뒤 공백 제거
    text = text.replace('_x000D_', '').replace('\r', '').replace('\n', ' ').strip()
    return text

model, tokenizer, device = load_model_and_tokenizer()

@app.post("/summarize")
async def summarize(request: Request):
    try:
        data = await request.json()
        text = data.get('text', '')
        print(f"Received text: {repr(text)}")

        if not text or not text.strip():
            return {"error": "No valid text provided"}

        text = preprocess_text(text)

        input_ids = tokenizer.encode(text, return_tensors='pt')
        print(f"Tokenized input shape: {input_ids.shape}")

        if input_ids.numel() == 0:
            return {"error": "Tokenized input is empty"}

        input_ids = input_ids.to(device)

        summary_ids = model.generate(
            input_ids,
            num_beams=params['num_beams'],
            max_length=params['max_length'],
            min_length=30,
            eos_token_id=tokenizer.eos_token_id,
            length_penalty=params['length_penalty'],
            no_repeat_ngram_size=params['no_repeat_ngram_size'],
            early_stopping=params['early_stopping'],
            decoder_start_token_id=tokenizer.cls_token_id  # 또는 bos_token_id
        )

        summary = tokenizer.decode(summary_ids[0], skip_special_tokens=True)
        print(f"Generated summary: {summary}")

        return {"summary": summary}

    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}
