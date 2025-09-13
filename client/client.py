import requests
import json
import os
import shutil
import time
from typing import Dict, List, Optional
import argparse
import logging
from pathlib import Path


class GridMRClient:
    def __init__(self, master_host=None, master_port=8080, nfs_path=None):
        # Usar variables de entorno o parámetros
        self.master_host = master_host or os.getenv("GRIDMR_MASTER_HOST", "localhost")
        self.master_port = master_port or int(os.getenv("GRIDMR_MASTER_PORT", "8080"))
        self.nfs_path = Path(nfs_path or os.getenv("GRIDMR_NFS_PATH", "/mnt/gridmr_nfs"))
        
        self.master_url = f"http://{self.master_host}:{self.master_port}"
        self.session = requests.Session()
        
        # Configurar logging
        logging.basicConfig(
            level=logging.INFO, 
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        self.logger = logging.getLogger(f"GridMRClient-{self.master_host}:{self.master_port}")
        
        # Configurar timeout por defecto para todas las requests
        self.session.timeout = 30
        
        # Detectar y configurar NFS
        self._setup_nfs_path()
        
        self.logger.info(f"Cliente inicializado - Master: {self.master_url}, NFS: {self.nfs_path}")
    
    def _setup_nfs_path(self):
        """Configurar la ruta NFS, usar directorio local si no está montado"""
        # Verificar si es EFS de AWS (mejor detección)
        if self._is_efs_or_network_mount():
            self.logger.info(f"Usando almacenamiento de red: {self.nfs_path}")
        elif not self.nfs_path.exists() or not os.path.ismount(str(self.nfs_path)):
            # Fallback a directorio local
            script_dir = Path(__file__).parent.absolute()
            local_nfs = script_dir.parent / "nfs_shared"
            
            if local_nfs.exists():
                self.nfs_path = local_nfs
                self.logger.info(f"Usando NFS local: {self.nfs_path}")
            else:
                # Crear directorio NFS local si no existe
                local_nfs.mkdir(parents=True, exist_ok=True)
                (local_nfs / "input").mkdir(exist_ok=True)
                (local_nfs / "output").mkdir(exist_ok=True)
                self.nfs_path = local_nfs
                self.logger.warning(f"Creado directorio NFS local: {self.nfs_path}")
        else:
            self.logger.info(f"Usando NFS montado: {self.nfs_path}")
    
    def _is_efs_or_network_mount(self) -> bool:
        """Detectar si es EFS de AWS o mount de red"""
        try:
            if not self.nfs_path.exists():
                return False
                
            # Verificar /proc/mounts para EFS o NFS4
            with open('/proc/mounts', 'r') as f:
                for line in f:
                    if str(self.nfs_path) in line and ('nfs4' in line or 'efs' in line):
                        return True
            
            # Verificación adicional para EFS
            try:
                stat_result = os.statvfs(str(self.nfs_path))
                return stat_result.f_fsid != 0
            except:
                pass
                
            return os.path.ismount(str(self.nfs_path))
        except:
            return False
            
    def upload_file(self, local_file: str, remote_name: Optional[str] = None) -> str:
        """Subir archivo al NFS compartido"""
        try:
            local_path = Path(local_file)
            if not local_path.exists():
                raise FileNotFoundError(f"Archivo local no encontrado: {local_file}")
                
            remote_name = remote_name or local_path.name
            remote_path = self.nfs_path / "input" / remote_name
            
            # Asegurar que el directorio input existe
            remote_path.parent.mkdir(parents=True, exist_ok=True)
            
            # Copiar archivo al NFS
            shutil.copy2(local_path, remote_path)
            
            # Verificar que la copia fue exitosa
            if not remote_path.exists():
                raise IOError(f"Error copiando archivo a {remote_path}")
            
            file_size = remote_path.stat().st_size
            self.logger.info(f"Archivo subido: {local_file} -> {remote_path} ({file_size} bytes)")
            
            return f"input/{remote_name}"
            
        except Exception as e:
            self.logger.error(f"Error subiendo archivo: {e}")
            raise
    
    def submit_job(self, job_config: Dict) -> str:
        """Enviar trabajo MapReduce al master via REST API"""
        try:
            endpoint = f"{self.master_url}/api/jobs/submit"
            
            # Validar configuración del trabajo
            self._validate_job_config(job_config)
            
            self.logger.info(f"Enviando trabajo a {endpoint}")
            self.logger.debug(f"Configuración del trabajo: {json.dumps(job_config, indent=2)}")
            
            response = self.session.post(
                endpoint, 
                json=job_config,
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )
            
            if response.status_code == 200 or response.status_code == 201:
                job_data = response.json()
                job_id = job_data.get('job_id')
                if not job_id:
                    raise ValueError("Respuesta del master no contiene job_id")
                    
                self.logger.info(f"Trabajo enviado exitosamente. Job ID: {job_id}")
                return job_id
            else:
                error_msg = f"HTTP {response.status_code}"
                try:
                    error_detail = response.json().get('error', response.text)
                    error_msg += f": {error_detail}"
                except:
                    error_msg += f": {response.text}"
                
                self.logger.error(f"Error enviando trabajo: {error_msg}")
                raise Exception(error_msg)
                
        except requests.exceptions.ConnectionError as e:
            self.logger.error(f"No se puede conectar al master en {self.master_url}")
            self.logger.error("Verificar:")
            self.logger.error("1. ¿Está el master ejecutándose?")
            self.logger.error("2. ¿Es correcta la dirección del master?")
            self.logger.error("3. ¿Están los puertos abiertos (Security Groups en AWS)?")
            raise ConnectionError(f"No se puede conectar al master: {e}")
        except requests.exceptions.Timeout as e:
            self.logger.error("Timeout conectando al master")
            raise TimeoutError(f"Timeout conectando al master: {e}")
        except Exception as e:
            self.logger.error(f"Error enviando trabajo: {e}")
            raise
    
    def _validate_job_config(self, job_config: Dict):
        """Validar configuración del trabajo"""
        required_fields = ['job_type', 'input_files']
        for field in required_fields:
            if field not in job_config:
                raise ValueError(f"Campo requerido faltante en job_config: {field}")
        
        valid_job_types = ['wordcount', 'sort', 'grep', 'linecount', 'unique']
        if job_config['job_type'] not in valid_job_types:
            raise ValueError(f"Tipo de trabajo no válido: {job_config['job_type']}. Válidos: {valid_job_types}")
        
        if not isinstance(job_config['input_files'], list) or not job_config['input_files']:
            raise ValueError("input_files debe ser una lista no vacía")
    
    def get_job_status(self, job_id: str) -> Dict:
        """Obtener estado del trabajo via REST API"""
        try:
            endpoint = f"{self.master_url}/api/jobs/{job_id}/status"
            response = self.session.get(endpoint, timeout=10)
            
            if response.status_code == 200:
                return response.json()
            elif response.status_code == 404:
                raise Exception(f"Trabajo no encontrado: {job_id}")
            else:
                error_msg = f"HTTP {response.status_code}: {response.text}"
                raise Exception(error_msg)
                
        except requests.exceptions.RequestException as e:
            self.logger.error(f"Error de conexión obteniendo estado del trabajo {job_id}: {e}")
            raise
        except Exception as e:
            self.logger.error(f"Error obteniendo estado del trabajo {job_id}: {e}")
            raise
    
    def wait_for_completion(self, job_id: str, timeout: int = 300, poll_interval: int = 5) -> Dict:
        """Esperar a que el trabajo complete"""
        start_time = time.time()
        last_status = None
        consecutive_errors = 0
        max_errors = 3
        
        self.logger.info(f"Esperando completación del trabajo {job_id} (timeout: {timeout}s)")
        
        while time.time() - start_time < timeout:
            try:
                status = self.get_job_status(job_id)
                consecutive_errors = 0  # Reset contador de errores
                
                # Solo imprimir si el estado cambió
                current_key_status = (status['status'], status.get('progress', 0))
                last_key_status = (last_status['status'], last_status.get('progress', 0)) if last_status else None
                
                if current_key_status != last_key_status:
                    progress = status.get('progress', 0)
                    self.logger.info(f"Estado del trabajo {job_id}: {status['status']} - Progreso: {progress}%")
                    
                    # Mostrar información adicional si está disponible
                    if 'current_phase' in status:
                        self.logger.info(f"Fase actual: {status['current_phase']}")
                    if 'tasks_completed' in status and 'total_tasks' in status:
                        self.logger.info(f"Tareas: {status['tasks_completed']}/{status['total_tasks']}")
                    
                    last_status = status.copy()
                
                if status['status'] == 'COMPLETED':
                    execution_time = status.get('execution_time_ms', 0) / 1000.0
                    self.logger.info(f"Trabajo completado exitosamente en {execution_time:.2f} segundos")
                    return status
                elif status['status'] == 'FAILED':
                    error_msg = status.get('error', 'Error desconocido')
                    self.logger.error(f"Trabajo falló: {error_msg}")
                    return status
                else:
                    time.sleep(poll_interval)
                    
            except Exception as e:
                consecutive_errors += 1
                self.logger.warning(f"Error consultando estado (intento {consecutive_errors}/{max_errors}): {e}")
                
                if consecutive_errors >= max_errors:
                    raise Exception(f"Demasiados errores consecutivos consultando estado del trabajo: {e}")
                
                time.sleep(poll_interval)
        
        raise TimeoutError(f"Trabajo {job_id} no completó en {timeout} segundos")
    
    def download_results(self, job_id: str, local_dir: str = "./results") -> List[str]:
        """Descargar resultados del NFS"""
        try:
            local_path = Path(local_dir)
            local_path.mkdir(parents=True, exist_ok=True)
            
            # Obtener información del trabajo
            status = self.get_job_status(job_id)
            
            if status['status'] != 'COMPLETED':
                raise Exception(f"Trabajo no completado: {status['status']}")
            
            # Buscar archivos de resultado
            output_files = status.get('output_files', [])
            
            # Si no hay archivos específicos, buscar en el directorio de salida
            if not output_files:
                output_dir = self.nfs_path / "output" / job_id
                if output_dir.exists():
                    output_files = [f"output/{job_id}/{f.name}" for f in output_dir.iterdir() if f.is_file()]
            
            if not output_files:
                # Buscar archivos con patrón común
                output_dir = self.nfs_path / "output"
                if output_dir.exists():
                    patterns = [f"*{job_id}*", "part-*", "output-*"]
                    for pattern in patterns:
                        matches = list(output_dir.glob(pattern))
                        if matches:
                            output_files = [f"output/{f.name}" for f in matches if f.is_file()]
                            break
            
            if not output_files:
                self.logger.warning("No se encontraron archivos de salida")
                return []
                
            downloaded_files = []
            
            for output_file in output_files:
                remote_path = self.nfs_path / output_file
                local_file = local_path / remote_path.name
                
                if remote_path.exists():
                    shutil.copy2(remote_path, local_file)
                    downloaded_files.append(str(local_file))
                    file_size = local_file.stat().st_size
                    self.logger.info(f"Descargado: {output_file} -> {local_file} ({file_size} bytes)")
                else:
                    self.logger.warning(f"Archivo no encontrado en NFS: {remote_path}")
            
            return downloaded_files
            
        except Exception as e:
            self.logger.error(f"Error descargando resultados: {e}")
            raise
    
    def list_workers(self) -> List[Dict]:
        """Listar workers activos via REST API"""
        try:
            endpoint = f"{self.master_url}/api/workers"
            response = self.session.get(endpoint, timeout=10)
            
            if response.status_code == 200:
                data = response.json()
                return data.get('workers', [])
            else:
                error_msg = f"HTTP {response.status_code}: {response.text}"
                raise Exception(error_msg)
                
        except Exception as e:
            self.logger.error(f"Error listando workers: {e}")
            raise
    
    def get_master_health(self) -> Dict:
        """Verificar salud del master"""
        try:
            endpoint = f"{self.master_url}/api/health"
            response = self.session.get(endpoint, timeout=5)
            
            if response.status_code == 200:
                return response.json()
            else:
                error_msg = f"HTTP {response.status_code}: {response.text}"
                raise Exception(error_msg)
                
        except Exception as e:
            self.logger.error(f"Error verificando salud del master: {e}")
            raise
    
    def cancel_job(self, job_id: str) -> bool:
        """Cancelar un trabajo en ejecución"""
        try:
            endpoint = f"{self.master_url}/api/jobs/{job_id}/cancel"
            response = self.session.post(endpoint, timeout=10)
            
            if response.status_code == 200:
                self.logger.info(f"Trabajo {job_id} cancelado exitosamente")
                return True
            else:
                error_msg = f"HTTP {response.status_code}: {response.text}"
                self.logger.error(f"Error cancelando trabajo: {error_msg}")
                return False
                
        except Exception as e:
            self.logger.error(f"Error cancelando trabajo {job_id}: {e}")
            return False
    
    def get_job_logs(self, job_id: str) -> str:
        """Obtener logs del trabajo"""
        try:
            endpoint = f"{self.master_url}/api/jobs/{job_id}/logs"
            response = self.session.get(endpoint, timeout=10)
            
            if response.status_code == 200:
                if 'application/json' in response.headers.get('content-type', ''):
                    data = response.json()
                    return data.get('logs', '')
                else:
                    return response.text
            else:
                error_msg = f"HTTP {response.status_code}: {response.text}"
                raise Exception(error_msg)
                
        except Exception as e:
            self.logger.error(f"Error obteniendo logs del trabajo {job_id}: {e}")
            raise
    
    def __enter__(self):
        """Context manager entry"""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit - cerrar session"""
        self.session.close()


def create_sample_input(filename: str = "test_input.txt", content_type: str = "words"):
    """Crear archivo de prueba"""
    sample_contents = {
        "words": [
            "hello world python programming",
            "distributed systems map reduce",
            "grid computing cluster processing",
            "hello python world",
            "map reduce programming systems",
            "python grid distributed cluster",
            "world processing computing hello"
        ],
        "numbers": [str(i) for i in range(1000, 2000)],
        "lines": [f"Line {i}: This is a test line with number {i}" for i in range(1, 101)]
    }
    
    content = sample_contents.get(content_type, sample_contents["words"])
    
    with open(filename, 'w') as f:
        f.write('\n'.join(content))
    
    print(f"Archivo de prueba creado: {filename} ({len(content)} líneas)")


def main():
    parser = argparse.ArgumentParser(description="Cliente GridMR - Comunicación REST con Master")
    parser.add_argument("--master-host", default=None, help="Host del master (usa GRIDMR_MASTER_HOST si no se especifica)")
    parser.add_argument("--master-port", type=int, default=8080, help="Puerto del master")
    parser.add_argument("--nfs-path", default=None, help="Ruta del NFS (usa GRIDMR_NFS_PATH si no se especifica)")
    parser.add_argument("--input-file", help="Archivo de entrada")
    parser.add_argument("--job-type", choices=["wordcount", "sort", "grep", "linecount", "unique"], 
                       default="wordcount", help="Tipo de trabajo")
    parser.add_argument("--output-dir", default="./results", help="Directorio de salida")
    parser.add_argument("--map-tasks", type=int, default=2, help="Número de tareas Map")
    parser.add_argument("--reduce-tasks", type=int, default=1, help="Número de tareas Reduce")
    parser.add_argument("--timeout", type=int, default=300, help="Timeout en segundos")
    parser.add_argument("--create-sample", action="store_true", help="Crear archivo de prueba")
    parser.add_argument("--verbose", "-v", action="store_true", help="Modo verbose (debug)")
    parser.add_argument("--monitor-only", help="Solo monitorear trabajo existente (Job ID)")
    
    args = parser.parse_args()
    
    # Configurar nivel de logging
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Crear archivo de prueba si se solicita
    if args.create_sample:
        create_sample_input("test_input.txt", "words")
        if not args.input_file:
            args.input_file = "test_input.txt"
    
    # Validar archivo de entrada si no es solo monitoreo
    if not args.monitor_only and not args.input_file:
        parser.error("Se requiere --input-file o --create-sample o --monitor-only")
    
    # Mostrar configuración que se va a usar
    master_host = args.master_host or os.getenv("GRIDMR_MASTER_HOST", "localhost")
    nfs_path = args.nfs_path or os.getenv("GRIDMR_NFS_PATH", "/mnt/gridmr_nfs")
    
    print("=== GridMR Client (REST API) ===")
    print(f"Master: {master_host}:{args.master_port}")
    print(f"NFS: {nfs_path}")
    print(f"Variables de entorno disponibles:")
    print(f"  GRIDMR_MASTER_HOST: {os.getenv('GRIDMR_MASTER_HOST', 'no configurado')}")
    print(f"  GRIDMR_NFS_PATH: {os.getenv('GRIDMR_NFS_PATH', 'no configurado')}")
    
    # Crear cliente REST usando context manager
    try:
        with GridMRClient(args.master_host, args.master_port, args.nfs_path) as client:
            
            # Solo monitorear trabajo existente
            if args.monitor_only:
                print(f"\nMonitoreando trabajo: {args.monitor_only}")
                try:
                    result = client.wait_for_completion(args.monitor_only, args.timeout)
                    if result['status'] == 'COMPLETED':
                        downloaded_files = client.download_results(args.monitor_only, args.output_dir)
                        print(f"Resultados descargados: {len(downloaded_files)} archivos")
                    else:
                        print(f"Trabajo terminó con estado: {result['status']}")
                except Exception as e:
                    print(f"Error monitoreando trabajo: {e}")
                return
            
            # 1. Verificar conectividad con el master
            print("\n1. Verificando conectividad con Master...")
            try:
                health = client.get_master_health()
                print(f"Master está activo: {health.get('status', 'UNKNOWN')}")
                print(f"Workers activos: {health.get('active_workers', 0)}")
                print(f"Tiempo de actividad: {health.get('uptime', 'N/A')}")
            except Exception as e:
                print(f"ERROR: No se puede conectar al master: {e}")
                print(f"Verificar que el master esté ejecutándose en http://{master_host}:{args.master_port}")
                return 1
            
            # 2. Verificar workers activos
            print("\n2. Verificando workers...")
            try:
                workers = client.list_workers()
                print(f"Workers activos: {len(workers)}")
                for i, worker in enumerate(workers):
                    status = worker.get('status', 'UNKNOWN')
                    load = worker.get('load', 'N/A')
                    print(f"   {i+1}. {worker.get('id', 'unknown')} @ {worker.get('address', 'unknown')} - Estado: {status} - Carga: {load}")
                
                if len(workers) == 0:
                    print("ERROR: No hay workers activos.")
                    print("Ejecutar primero: ./scripts/start_system.sh")
                    return 1
            except Exception as e:
                print(f"WARNING: No se pudieron listar workers: {e}")
                workers = []
            
            # 3. Subir archivo de entrada
            print(f"\n3. Subiendo archivo: {args.input_file}")
            if not os.path.exists(args.input_file):
                print(f"ERROR: Archivo no encontrado: {args.input_file}")
                return 1
                
            try:
                input_path = client.upload_file(args.input_file)
                print(f"Archivo subido como: {input_path}")
            except Exception as e:
                print(f"ERROR subiendo archivo: {e}")
                return 1
            
            # 4. Configurar trabajo
            max_workers = len(workers) if workers else 4
            job_config = {
                "job_type": args.job_type,
                "input_files": [input_path],
                "parameters": {
                    "map_tasks": min(args.map_tasks, max_workers),
                    "reduce_tasks": min(args.reduce_tasks, max_workers)
                }
            }
            
            # Agregar parámetros específicos para grep
            if args.job_type == "grep":
                pattern = input("Ingrese el patrón a buscar: ").strip()
                if not pattern:
                    print("ERROR: Patrón vacío para grep")
                    return 1
                job_config["parameters"]["pattern"] = pattern
            
            # 5. Enviar trabajo via REST
            print(f"\n4. Enviando trabajo MapReduce...")
            print(f"Tipo: {args.job_type}")
            print(f"Tareas Map: {job_config['parameters']['map_tasks']}")
            print(f"Tareas Reduce: {job_config['parameters']['reduce_tasks']}")
            
            try:
                job_id = client.submit_job(job_config)
                print(f"Job ID: {job_id}")
            except Exception as e:
                print(f"ERROR enviando trabajo: {e}")
                return 1
            
            # 6. Esperar finalización
            print(f"\n5. Monitoreando ejecución...")
            try:
                result = client.wait_for_completion(job_id, args.timeout)
            except KeyboardInterrupt:
                print("\nCancelando trabajo...")
                if client.cancel_job(job_id):
                    print("Trabajo cancelado exitosamente")
                else:
                    print("No se pudo cancelar el trabajo")
                return 1
            except Exception as e:
                print(f"ERROR monitoreando trabajo: {e}")
                # Intentar obtener logs del error
                try:
                    logs = client.get_job_logs(job_id)
                    if logs:
                        print(f"Logs del trabajo:\n{logs}")
                except:
                    pass
                return 1
            
            # 7. Descargar y mostrar resultados
            if result['status'] == 'COMPLETED':
                print(f"\n6. Descargando resultados...")
                try:
                    downloaded_files = client.download_results(job_id, args.output_dir)
                    
                    print(f"\n✅ Trabajo completado exitosamente!")
                    print(f"Tiempo de ejecución: {result.get('execution_time_ms', 0) / 1000.0:.2f} segundos")
                    print(f"Archivos de resultado: {len(downloaded_files)}")
                    
                    for file_path in downloaded_files:
                        file_size = os.path.getsize(file_path)
                        print(f"   - {file_path} ({file_size:,} bytes)")
                    
                    # Mostrar preview de los resultados
                    if downloaded_files:
                        print(f"\n Preview del resultado ({args.job_type}):")
                        try:
                            with open(downloaded_files[0], 'r') as f:
                                lines_shown = 0
                                for line in f:
                                    if lines_shown >= 10:
                                        total_lines = sum(1 for _ in open(downloaded_files[0]))
                                        print(f"   [...{total_lines - 10} líneas más...]")
                                        break
                                    print(f"   {line.strip()}")
                                    lines_shown += 1
                        except Exception as e:
                            print(f"   Error mostrando preview: {e}")
                            
                except Exception as e:
                    print(f"ERROR descargando resultados: {e}")
                    return 1
            else:
                print(f"\nTrabajo falló: {result.get('error', 'Error desconocido')}")
                
                # Intentar mostrar logs del error
                try:
                    logs = client.get_job_logs(job_id)
                    if logs:
                        print(f"\nLogs del trabajo:\n{logs}")
                except Exception as e:
                    print(f"No se pudieron obtener los logs: {e}")
                
                return 1
    
    except KeyboardInterrupt:
        print("\n\nOperación cancelada por el usuario")
        return 1
    except Exception as e:
        print(f"ERROR: {e}")
        return 1
    
    return 0


if __name__ == "__main__":
    exit(main())