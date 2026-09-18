import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from .database import init_db
from .api.admin import router as admin_router
from .api.report import router as report_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize SQLite tables and indices on startup
    await init_db()
    yield

app = FastAPI(
    title="NetCheck Diagnostic Server",
    description="Network diagnostic backend providing base64 command generation and report collection",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware to allow Android clients and browser cross-origin requests
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routers
app.include_router(admin_router)
app.include_router(report_router)

# Mount Web Dashboard
@app.get("/", response_class=HTMLResponse)
@app.head("/", response_class=HTMLResponse)
async def read_index():
    template_path = os.path.join(os.path.dirname(__file__), "templates", "index.html")
    with open(template_path, "r", encoding="utf-8") as f:
        html_content = f.read()
    return HTMLResponse(content=html_content)

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "netcheck-backend"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.main:app", host="0.0.0.0", port=3000, reload=True)
