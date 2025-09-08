# Project1-TET

## Run without Docker

1. Create and activate a Python 3.12 virtual environment.
2. Install dependencies:

```bash
pip install -r serv_persistor/requirements.txt
```

3. Start your master REST API locally (expected default: http://localhost:8080/api).

4. Optionally set a custom URL for the master REST endpoint:

```bash
# Windows PowerShell
$env:MASTER_REST_URL = "http://localhost:8080/api"

# CMD
set MASTER_REST_URL=http://localhost:8080/api

# Unix shells
export MASTER_REST_URL=http://localhost:8080/api
```

5. Run the persistor service:

```bash
python serv_persistor/main.py
```