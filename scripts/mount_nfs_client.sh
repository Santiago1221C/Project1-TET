#!/bin/bash

# Script para montar NFS en máquinas cliente (Workers y Master)
# Ejecutar con sudo

NFS_SERVER_IP=${1:-"192.168.1.100"}  # IP del servidor NFS
MOUNT_POINT="/mnt/gridmr_nfs"

echo "=== Configurando NFS Client ==="
echo "Servidor NFS: $NFS_SERVER_IP"

# 1. Instalar cliente NFS
apt-get update
apt-get install -y nfs-common

# 2. Crear punto de montaje
mkdir -p $MOUNT_POINT

# 3. Montar NFS
mount -t nfs $NFS_SERVER_IP:/home/gridmr/nfs_shared $MOUNT_POINT

# 4. Verificar montaje
if mountpoint -q $MOUNT_POINT; then
    echo "NFS montado correctamente en $MOUNT_POINT"
    ls -la $MOUNT_POINT
else
    echo "Error al montar NFS"
    exit 1
fi

# 5. Configurar montaje automático en /etc/fstab
echo "$NFS_SERVER_IP:/home/gridmr/nfs_shared $MOUNT_POINT nfs defaults 0 0" >> /etc/fstab

echo "=== NFS Client configurado ==="
echo "Directorio local: $MOUNT_POINT"