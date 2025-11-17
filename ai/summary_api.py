import os
import torch
import logging
import traceback
from fastapi import FastAPI, Request
from transformers import PreTrainedTokenizerFast, BartForConditionalGeneration, AutoConfig

# ------------------------------------------------------------
# ⚙️ 환경 설정 (CUDA, 병렬 처리 제한, 경고 최소화)
# ------------------------------------------------------------
os.environ["CUDA_VISIBLE_DEVICES"] = "0"
os.environ["TOKENIZERS_PARALLELISM"] = "false"
os.environ["TRANSFORMERS_NO_ADVISORY_WARNINGS"] = "true"
logging.disable(logging.INFO)
logging.disable(logging.WARNING)

app = FastAPI()

# ------------------------------------------------------------
# 📦 모델 정보
# ------------------------------------------------------------
model_name = 'gogamza/kobart-base-v2'
model_path = 'C:/Users/1/Desktop/Downloads/workguard/src/main/resources/models/ai/checkpoint-26606/'

params = {
    'num_beams': 4,
    'max_length': 200,
    'length_penalty': 0.5,
    'no_repeat_ngram_size': 3,
    'early_stopping': True
}

# ------------------------------------------------------------
# 🧠 모델과 토크나이저 로드
# ------------------------------------------------------------
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

# 전역 변수로 로드 (첫 요청 지연 최소화)
model, tokenizer, device = load_model_and_tokenizer()

# ------------------------------------------------------------
# 🧹 텍스트 전처리
# ------------------------------------------------------------
def preprocess_text(text: str) -> str:
    return text.replace('_x000D_', '').replace('\r', '').replace('\n', ' ').strip()

# ------------------------------------------------------------
# 🪄 요약 API
# ------------------------------------------------------------
@app.post("/summarize")
async def summarize(request: Request):
    try:
        data = await request.json()
        text = data.get('text', '')
        print(f"입력 텍스트: {repr(text[:100])}...")  # 앞 100자만 출력

        if not text or not text.strip():
            return {"error": "No valid text provided"}

        text = preprocess_text(text)

        input_ids = tokenizer.encode(text, return_tensors='pt', truncation=True, max_length=1024)
        if input_ids.numel() == 0:
            return {"error": "Tokenized input is empty"}

        input_ids = input_ids.to(device)

        with torch.no_grad():  # 속도 + 메모리 최적화
            summary_ids = model.generate(
                input_ids,
                num_beams=params['num_beams'],
                max_length=params['max_length'],
                eos_token_id=tokenizer.eos_token_id,
                length_penalty=params['length_penalty'],
                no_repeat_ngram_size=params['no_repeat_ngram_size'],
                early_stopping=params['early_stopping']
            )

        summary = tokenizer.decode(summary_ids[0], skip_special_tokens=True)
        print(f"생성 요약: {summary}")

        return {"summary": summary}

    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}
