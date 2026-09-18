import json
from fastapi import APIRouter, HTTPException
from ..database import get_db_connection
from ..models import ReportPayloadSchema

router = APIRouter(prefix="/api/reports", tags=["reports"])

@router.post("")
@router.post("/")
async def submit_report(payload: ReportPayloadSchema):
    db = await get_db_connection()
    try:
        device_info_str = json.dumps(payload.device_info, ensure_ascii=False)
        network_env_str = json.dumps(payload.network_env, ensure_ascii=False)
        task_results_str = json.dumps(payload.results, ensure_ascii=False)

        await db.execute(
            """
            INSERT INTO diagnostic_reports (tracking_id, device_info, network_env, task_results, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(tracking_id) DO UPDATE SET
                device_info = excluded.device_info,
                network_env = excluded.network_env,
                task_results = excluded.task_results,
                created_at = CURRENT_TIMESTAMP
            """,
            (payload.tracking_id, device_info_str, network_env_str, task_results_str)
        )
        await db.commit()
        return {
            "status": "success",
            "message": "Report uploaded successfully",
            "tracking_id": payload.tracking_id
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await db.close()

@router.get("")
@router.get("/")
async def list_reports(limit: int = 50):
    db = await get_db_connection()
    try:
        cursor = await db.execute(
            """
            SELECT id, tracking_id, device_info, network_env, task_results, created_at
            FROM diagnostic_reports
            ORDER BY created_at DESC
            LIMIT ?
            """,
            (limit,)
        )
        rows = await cursor.fetchall()
        result = []
        for r in rows:
            try:
                device_info = json.loads(r["device_info"])
            except Exception:
                device_info = {}
            try:
                network_env = json.loads(r["network_env"])
            except Exception:
                network_env = {}
            try:
                task_results = json.loads(r["task_results"])
            except Exception:
                task_results = []
            
            result.append({
                "id": r["id"],
                "tracking_id": r["tracking_id"],
                "device_info": device_info,
                "network_env": network_env,
                "task_results": task_results,
                "created_at": r["created_at"]
            })
        return result
    finally:
        await db.close()

@router.get("/{tracking_id}")
async def get_report(tracking_id: str):
    db = await get_db_connection()
    try:
        cursor = await db.execute(
            """
            SELECT id, tracking_id, device_info, network_env, task_results, created_at
            FROM diagnostic_reports
            WHERE tracking_id = ?
            """,
            (tracking_id.strip().upper(),)
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail=f"Report with tracking_id '{tracking_id}' not found")

        try:
            device_info = json.loads(row["device_info"])
        except Exception:
            device_info = {}
        try:
            network_env = json.loads(row["network_env"])
        except Exception:
            network_env = {}
        try:
            task_results = json.loads(row["task_results"])
        except Exception:
            task_results = []

        return {
            "id": row["id"],
            "tracking_id": row["tracking_id"],
            "device_info": device_info,
            "network_env": network_env,
            "task_results": task_results,
            "created_at": row["created_at"]
        }
    finally:
        await db.close()
