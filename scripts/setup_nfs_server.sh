# Script para configurar NFS Server
# Ejecutar con: sudo ./scripts/setup_nfs_server.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NFS_SHARED_DIR="$PROJECT_ROOT/nfs_shared"

echo "=== Configurando NFS Server para GridMR ==="
echo "Directorio del proyecto: $PROJECT_ROOT"
echo "Directorio NFS: $NFS_SHARED_DIR"

# Verificar que se ejecute como root
if [ "$EUID" -ne 0 ]; then
    echo "Este script debe ejecutarse con sudo"
    exit 1
fi

# 1. Instalar NFS Server (Ubuntu/Debian)
echo "Instalando NFS Server..."
apt-get update -qq
apt-get install -y nfs-kernel-server

# 2. Crear estructura de directorios
echo "Creando estructura de directorios..."
mkdir -p "$NFS_SHARED_DIR"/{input,intermediate,output,metadata}

# 3. Configurar permisos
echo "Configurando permisos..."
chown -R $SUDO_USER:$SUDO_USER "$NFS_SHARED_DIR"
chmod -R 755 "$NFS_SHARED_DIR"

# 4. Configurar /etc/exports
echo "Configurando exports..."
EXPORT_LINE="$NFS_SHARED_DIR *(rw,sync,no_subtree_check,insecure,no_root_squash)"

# Remover línea existente si existe
grep -v "$NFS_SHARED_DIR" /etc/exports > /tmp/exports.tmp 2>/dev/null || touch /tmp/exports.tmp
echo "$EXPORT_LINE" >> /tmp/exports.tmp
mv /tmp/exports.tmp /etc/exports

# 5. Aplicar configuración
echo "Aplicando configuración NFS..."
exportfs -ra
systemctl restart nfs-kernel-server
systemctl enable nfs-kernel-server

# 6. Configurar firewall si está activo
if systemctl is-active --quiet ufw; then
    echo "Configurando firewall..."
    ufw allow nfs
    ufw allow 2049
fi

# 7. Verificar configuración
echo "Verificando configuración..."
if showmount -e localhost &>/dev/null; then
    echo "NFS Server configurado correctamente"
    echo "Exports actuales:"
    showmount -e localhost
else
    echo "Error en la configuración NFS"
    exit 1
fi

echo ""
echo "=== Configuración Completada ==="
echo "Directorio compartido: $NFS_SHARED_DIR"
echo "Para conectar desde clientes:"
echo "sudo mount -t nfs $(hostname -I | awk '{print $1}'):$NFS_SHARED_DIR /mnt/gridmr_nfs"
echo ""
echo "Para iniciar el sistema: ./scripts/start_system.sh"