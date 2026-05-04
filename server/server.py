#!/usr/bin/env python3
"""
API REST para coleta de fotos de conversores.
Recebe requisições do APK Android e gerencia as pastas de dataset localmente.
"""

import io
import json
import logging
import re
import shutil
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from fastapi import FastAPI, UploadFile, HTTPException, Depends
from fastapi.responses import FileResponse, Response
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from PIL import Image
from torchvision import transforms

# Adiciona o diretório ml ao path para importar train
ML_DIR = Path(__file__).resolve().parent.parent / "ml"
sys.path.insert(0, str(ML_DIR))

BASE_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = BASE_DIR / "ml" / "datasets"
DATASET_DIR.mkdir(parents=True, exist_ok=True)

APK_PATH = Path(__file__).resolve().parent / "app.apk"
APP_VERSION_CODE = 14

ALLOWED_EXT = {"jpg", "jpeg", "png", "webp", "bmp"}
PLACEHOLDER_NAME = "00.jpg"

# Magic bytes para detectar tipo de imagem (substitui imghdr que foi removido)
_SIGNATURES = {
    b"\xff\xd8\xff": "jpg",
    b"\x89PNG\r\n\x1a\n": "png",
    b"RIFF": "webp",  # WebP começa com RIFF....WEBP
    b"BM": "bmp",
}

# Credenciais fixas
USERS = {
    ***REMOVED***,
    ***REMOVED***
}

app = FastAPI(title="CNN Fotos API")
security = HTTPBasic()


# --- Auth ---

def auth(credentials: HTTPBasicCredentials = Depends(security)):
    if USERS.get(credentials.username) != credentials.password:
        raise HTTPException(401, "Credenciais inválidas")
    return credentials.username


# --- Helpers ---

def sanitize(name: str) -> str:
    name = (name or "").strip().replace(" ", "_")
    return "".join(c for c in name if c.isalnum() or c in ("-", "_"))


def guess_ext(filepath: Path) -> str | None:
    header = filepath.read_bytes()[:16]
    for magic, ext in _SIGNATURES.items():
        if header.startswith(magic):
            if ext == "webp" and b"WEBP" not in header:
                continue
            return ext
    return None


# --- Endpoints ---

@app.get("/conversores")
def listar_conversores(_user: str = Depends(auth)):
    dirs = sorted(p.name for p in DATASET_DIR.iterdir() if p.is_dir())
    return {"conversores": dirs}


@app.post("/conversores")
def criar_conversor(nome: str, _user: str = Depends(auth)):
    nome = sanitize(nome)
    if not nome:
        raise HTTPException(400, "Nome inválido")
    pasta = DATASET_DIR / nome
    pasta.mkdir(parents=True, exist_ok=True)
    (pasta / PLACEHOLDER_NAME).touch()
    return {"criado": nome}


@app.patch("/conversores/{conversor}")
def renomear_conversor(conversor: str, novo_nome: str, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    novo_nome = sanitize(novo_nome)
    if not novo_nome:
        raise HTTPException(400, "Novo nome inválido")
    pasta_atual = DATASET_DIR / conversor
    if not pasta_atual.exists():
        raise HTTPException(404, "Conversor não encontrado")
    pasta_nova = DATASET_DIR / novo_nome
    if pasta_nova.exists():
        raise HTTPException(409, "Já existe um conversor com esse nome")
    pasta_atual.rename(pasta_nova)
    return {"renomeado": {"de": conversor, "para": novo_nome}}


@app.delete("/conversores/{conversor}")
def deletar_conversor(conversor: str, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    pasta = DATASET_DIR / conversor
    if not pasta.exists():
        raise HTTPException(404, "Conversor não encontrado")
    shutil.rmtree(pasta)
    return {"deletado": conversor}


@app.get("/conversores/{conversor}/fotos")
def listar_fotos(conversor: str, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    pasta = DATASET_DIR / conversor
    if not pasta.exists():
        raise HTTPException(404, "Conversor não encontrado")
    fotos = []
    for f in sorted(pasta.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if f.is_file() and f.name != PLACEHOLDER_NAME and f.suffix.lower().lstrip(".") in ALLOWED_EXT:
            fotos.append({
                "nome": f.name,
                "tamanho_kb": max(1, f.stat().st_size // 1024),
            })
    return {"conversor": conversor, "total": len(fotos), "fotos": fotos}


@app.post("/conversores/{conversor}/fotos")
async def enviar_fotos(conversor: str, fotos: list[UploadFile], _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    if not conversor:
        raise HTTPException(400, "Conversor inválido")
    pasta = DATASET_DIR / conversor
    pasta.mkdir(parents=True, exist_ok=True)

    salvos = 0
    falhas = 0

    for foto in fotos:
        try:
            ts = time.strftime("%Y%m%d_%H%M%S")
            nome_orig = foto.filename or ""
            prefix_match = re.match(r"^(f\d{2}_)", nome_orig)
            prefix = prefix_match.group(1) if prefix_match else ""
            base = f"{prefix}{conversor}_{ts}_{int(time.time()*1000)%1000:03d}"
            tmp = pasta / f"{base}.uploading"

            conteudo = await foto.read()
            tmp.write_bytes(conteudo)

            ext = guess_ext(tmp)
            if ext is None:
                nome_orig = foto.filename or ""
                if "." in nome_orig and nome_orig.rsplit(".", 1)[1].lower() in ALLOWED_EXT:
                    ext = nome_orig.rsplit(".", 1)[1].lower()
                else:
                    tmp.unlink(missing_ok=True)
                    falhas += 1
                    continue

            if ext not in ALLOWED_EXT:
                tmp.unlink(missing_ok=True)
                falhas += 1
                continue

            final = pasta / f"{base}.{ext}"
            tmp.rename(final)
            salvos += 1
        except Exception:
            falhas += 1

    return {"salvos": salvos, "falhas": falhas}


@app.delete("/conversores/{conversor}/fotos/{arquivo}")
def deletar_foto(conversor: str, arquivo: str, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    caminho = DATASET_DIR / conversor / arquivo
    if not caminho.exists() or not caminho.is_file():
        raise HTTPException(404, "Foto não encontrada")
    caminho.unlink()
    return {"deletado": arquivo}


@app.get("/conversores/{conversor}/fotos/{arquivo}/thumb")
def thumb_foto(conversor: str, arquivo: str, size: int = 300, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    caminho = DATASET_DIR / conversor / arquivo
    if not caminho.exists() or not caminho.is_file():
        raise HTTPException(404, "Foto não encontrada")
    with Image.open(caminho) as img:
        img.thumbnail((size, size), Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.convert("RGB").save(buf, format="JPEG", quality=70, optimize=True)
    return Response(content=buf.getvalue(), media_type="image/jpeg")


@app.get("/conversores/{conversor}/fotos/{arquivo}/download")
def baixar_foto(conversor: str, arquivo: str, _user: str = Depends(auth)):
    conversor = sanitize(conversor)
    caminho = DATASET_DIR / conversor / arquivo
    if not caminho.exists() or not caminho.is_file():
        raise HTTPException(404, "Foto não encontrada")
    return FileResponse(caminho)


# --- Inferência ---

MODEL_DIR = ML_DIR / "output" / "models"
MEAN = np.array([0.485, 0.456, 0.406])
STD = np.array([0.229, 0.224, 0.225])

_infer_state: dict = {}

log = logging.getLogger("uvicorn.error")


def _load_model():
    """Lazy load do modelo na primeira chamada."""
    if _infer_state.get("model") is not None:
        return

    meta_path = MODEL_DIR / "meta.json"
    model_path = MODEL_DIR / "best_model.pt"

    if not meta_path.exists() or not model_path.exists():
        raise HTTPException(503, "Modelo não disponível — treine primeiro")

    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    from train import build_model

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build_model(meta["num_classes"], device)
    model.load_state_dict(torch.load(model_path, map_location=device, weights_only=True))
    model.eval()

    transform = transforms.Compose([
        transforms.Resize(meta["img_size"], interpolation=transforms.InterpolationMode.BICUBIC),
        transforms.CenterCrop(meta["img_size"]),
        transforms.ToTensor(),
        transforms.Normalize(mean=MEAN.tolist(), std=STD.tolist()),
    ])

    _infer_state.update({
        "model": model,
        "device": device,
        "transform": transform,
        "class_names": meta["class_names"],
        "img_size": meta["img_size"],
        "num_classes": meta["num_classes"],
    })
    log.info(f"Modelo carregado | {device} | {meta['num_classes']} classes | {meta['img_size']}px")


@torch.no_grad()
def _predict(img: Image.Image, tta: bool = False):
    """Roda inferência simples ou com TTA (4 flips + 3 rotações = 7 augs, igual ao train.py)."""
    s = _infer_state
    model, device, transform = s["model"], s["device"], s["transform"]

    tensor = transform(img).unsqueeze(0).to(device)
    if device.type == "cuda":
        tensor = tensor.to(memory_format=torch.channels_last)

    if not tta:
        logits = model(tensor)
        return F.softmax(logits, dim=1)[0].cpu().numpy()

    augs = [
        tensor,
        torch.flip(tensor, [-1]),
        torch.flip(tensor, [-2]),
        torch.flip(tensor, [-1, -2]),
        torch.rot90(tensor, 1, [-2, -1]),
        torch.rot90(tensor, 2, [-2, -1]),
        torch.rot90(tensor, 3, [-2, -1]),
    ]
    probs_sum = torch.zeros(1, _infer_state["num_classes"], device=device)
    for aug in augs:
        probs_sum += F.softmax(model(aug), dim=1)
    return (probs_sum / len(augs))[0].cpu().numpy()


@app.post("/infer")
async def inferir(foto: UploadFile, tta: bool = False, _user: str = Depends(auth)):
    _load_model()

    conteudo = await foto.read()
    try:
        img = Image.open(io.BytesIO(conteudo)).convert("RGB")
    except Exception:
        raise HTTPException(400, "Imagem inválida")

    probs = _predict(img, tta=tta)

    top_k = min(len(_infer_state["class_names"]), 5)
    top_idx = np.argsort(probs)[::-1][:top_k]

    top5 = []
    for idx in top_idx:
        top5.append({
            "class": _infer_state["class_names"][idx],
            "confidence": round(float(probs[idx]) * 100, 1),
        })

    return {
        "class": top5[0]["class"],
        "confidence": top5[0]["confidence"],
        "tta": tta,
        "top5": top5,
    }


@app.get("/infer/status")
def infer_status(_user: str = Depends(auth)):
    meta_path = MODEL_DIR / "meta.json"
    if not meta_path.exists():
        return {"ready": False, "reason": "modelo não treinado"}

    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    return {
        "ready": True,
        "loaded": _infer_state.get("model") is not None,
        "num_classes": meta["num_classes"],
        "img_size": meta["img_size"],
        "class_names": meta["class_names"],
    }


@app.get("/app/version")
def app_version():
    return {"versionCode": APP_VERSION_CODE}


@app.get("/app/download")
def app_download():
    if not APK_PATH.exists():
        raise HTTPException(404, "APK não encontrado")
    return FileResponse(
        APK_PATH,
        media_type="application/vnd.android.package-archive",
        filename="cnn.apk",
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=52500)