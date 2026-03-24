import os
import glob
import random
import shutil
import csv
import importlib.util
import importlib.machinery
from collections import Counter


def _ensure_dirs(base_dir):
    os.makedirs(base_dir, exist_ok=True)
    for d in ["data_EDF", "data_EDF_processed", "csv_output", "final_output", "picture"]:
        os.makedirs(os.path.join(base_dir, d), exist_ok=True)


def _csv_name_by_edf(edf_name: str) -> str:
    return os.path.splitext(edf_name)[0] + "_emotions.csv"


def _dep_available(name: str) -> bool:
    return importlib.util.find_spec(name) is not None


def get_dependency_report():
    required = ["numpy", "pandas", "matplotlib", "mne", "scipy", "sklearn", "torch", "transformers"]
    available = {name: _dep_available(name) for name in required}
    return {
        "required": required,
        "available": available,
        "real_model1_ready": all(available.get(k, False) for k in ["numpy", "pandas", "mne", "scipy", "sklearn"]),
        "real_model2_ready": all(available.get(k, False) for k in ["numpy", "pandas", "matplotlib", "torch", "transformers"]),
        "note": "端侧优先轻量链路；重依赖不足时自动回退。",
    }


def format_dependency_report(report: dict) -> str:
    available = report.get("available", {})
    parts = []
    for name in ["numpy", "pandas", "matplotlib", "mne", "scipy", "sklearn", "torch", "transformers"]:
        parts.append(f"{name}:{'OK' if available.get(name) else 'NO'}")
    parts.append(f"real_m1:{'OK' if report.get('real_model1_ready') else 'NO'}")
    parts.append(f"real_m2:{'OK' if report.get('real_model2_ready') else 'NO'}")
    return " | ".join(parts)


def _try_real_model1(base_dir: str, edf_name: str):
    model1_path = os.path.join(base_dir, "Model_1.py")
    if not os.path.exists(model1_path):
        raise RuntimeError("Model_1.py not found in base_dir")

    loader = importlib.machinery.SourceFileLoader("model1_runtime", model1_path)
    mod = loader.load_module()

    if not hasattr(mod, "process_single_edf"):
        raise RuntimeError("Model_1.py missing process_single_edf")

    mod.process_single_edf(edf_name, None)
    csv_path = os.path.join(base_dir, "csv_output", _csv_name_by_edf(edf_name))
    if not os.path.exists(csv_path):
        raise RuntimeError("Model_1 ran but CSV not generated")
    return csv_path


def run_model1_light(base_dir: str, edf_name: str):
    _ensure_dirs(base_dir)

    csv_name = _csv_name_by_edf(edf_name)
    csv_path = os.path.join(base_dir, "csv_output", csv_name)
    if os.path.exists(csv_path):
        return {"ok": True, "csv": csv_path, "mode": "reuse_existing_csv"}

    labels = ["happy", "sad", "neutral", "angry"]
    rows = []
    for i in range(24):
        lab = random.choices(labels, weights=[4, 2, 11, 3], k=1)[0]
        row = {
            "t_start_s": i * 1.5,
            "prediction": lab,
            "proba_happy": 0.1,
            "proba_sad": 0.1,
            "proba_neutral": 0.7,
            "proba_angry": 0.1,
        }
        if lab == "happy":
            row["proba_happy"] = 0.72
            row["proba_neutral"] = 0.18
        elif lab == "sad":
            row["proba_sad"] = 0.72
            row["proba_neutral"] = 0.18
        elif lab == "angry":
            row["proba_angry"] = 0.72
            row["proba_neutral"] = 0.18
        rows.append(row)

    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "t_start_s", "prediction", "proba_happy", "proba_sad", "proba_neutral", "proba_angry"
        ])
        writer.writeheader()
        writer.writerows(rows)

    return {"ok": True, "csv": csv_path, "mode": "mock_generated_csv"}


def run_model2_light(base_dir: str, csv_path: str):
    _ensure_dirs(base_dir)

    counts = Counter()
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            lab = (row.get("prediction") or "neutral").strip().lower()
            counts[lab] += 1

    if not counts:
        counts["neutral"] = 1

    dominant4 = counts.most_common(1)[0][0]
    mapping = {
        "happy": "joy",
        "sad": "sadness",
        "neutral": "neutral",
        "angry": "anger",
        "joy": "joy",
        "sadness": "sadness",
        "anger": "anger",
        "disgust": "disgust",
        "fear": "fear",
        "surprise": "surprise",
    }
    dominant7 = mapping.get(dominant4, "neutral")

    final_dir = os.path.join(base_dir, "final_output")
    pie_path = os.path.join(final_dir, "emotion_distribution.png")
    result_path = os.path.join(final_dir, "result.jpg")

    if not os.path.exists(pie_path):
        with open(pie_path, "wb") as f:
            f.write(b"")

    pic_dir = os.path.join(base_dir, "picture", dominant7)
    candidates = []
    for ext in ["*.jpg", "*.jpeg", "*.png", "*.webp"]:
        candidates.extend(glob.glob(os.path.join(pic_dir, ext)))

    chosen = ""
    if candidates:
        chosen = random.choice(candidates)
        if os.path.exists(result_path):
            try:
                os.remove(result_path)
            except Exception:
                pass
        # Android 上 copy2 可能因元数据复制触发权限问题，改用 copyfile 更稳。
        shutil.copyfile(chosen, result_path)

    return {
        "ok": True,
        "dominant": dominant7,
        "counts": dict(counts),
        "pie": pie_path,
        "result": result_path,
        "source_image": chosen,
    }


def run_pipeline_auto(base_dir: str, edf_name: str):
    _ensure_dirs(base_dir)
    dep = get_dependency_report()

    strategy = "lightweight"
    model1_msg = ""
    used_fallback = False

    if dep.get("real_model1_ready"):
        try:
            csv_path = _try_real_model1(base_dir, edf_name)
            m1 = {"ok": True, "csv": csv_path, "mode": "real_model1"}
            strategy = "real_m1 + light_m2"
            model1_msg = "真实Model_1执行成功"
        except Exception as e:
            used_fallback = True
            m1 = run_model1_light(base_dir, edf_name)
            model1_msg = f"真实Model_1失败，已回退轻量链路: {e}"
    else:
        m1 = run_model1_light(base_dir, edf_name)
        model1_msg = "依赖不足，使用轻量Model_1"

    m2 = run_model2_light(base_dir, m1["csv"])
    model2_msg = "使用轻量Model_2（CSV统计+随机抽图）"

    return {
        "ok": True,
        "strategy": strategy,
        "used_fallback": used_fallback,
        "dependency_report": dep,
        "dependency_text": format_dependency_report(dep),
        "model1_message": model1_msg,
        "model2_message": model2_msg,
        "model1": m1,
        "model2": m2,
    }


def run_pipeline_mode(base_dir: str, edf_name: str, mode: str):
    _ensure_dirs(base_dir)
    dep = get_dependency_report()
    dep_text = format_dependency_report(dep)

    if mode == "real":
        # 仅真实：如果真实依赖不足则直接返回失败信息
        if not dep.get("real_model1_ready"):
            return {
                "ok": False,
                "strategy": "real",
                "dependency_text": dep_text,
                "model1_message": "真实Model_1依赖不足",
                "model2_message": "未执行",
            }
        try:
            csv_path = _try_real_model1(base_dir, edf_name)
            m2 = run_model2_light(base_dir, csv_path)
            return {
                "ok": True,
                "strategy": "real_m1 + light_m2",
                "dependency_text": dep_text,
                "model1_message": "真实Model_1执行成功",
                "model2_message": "使用轻量Model_2（CSV统计+随机抽图）",
                "model1": {"ok": True, "csv": csv_path, "mode": "real_model1"},
                "model2": m2,
            }
        except Exception as e:
            return {
                "ok": False,
                "strategy": "real",
                "dependency_text": dep_text,
                "model1_message": f"真实Model_1执行失败: {e}",
                "model2_message": "未执行",
            }

    if mode == "light":
        m1 = run_model1_light(base_dir, edf_name)
        m2 = run_model2_light(base_dir, m1["csv"])
        return {
            "ok": True,
            "strategy": "light",
            "dependency_text": dep_text,
            "model1_message": "使用轻量Model_1",
            "model2_message": "使用轻量Model_2（CSV统计+随机抽图）",
            "model1": m1,
            "model2": m2,
        }

    # auto
    return run_pipeline_auto(base_dir, edf_name)


def run_pipeline(base_dir: str, edf_name: str):
    """向后兼容旧版 Java 调用。"""
    return run_pipeline_auto(base_dir, edf_name)
