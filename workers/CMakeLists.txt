cmake_minimum_required(VERSION 3.20)
project(Worker)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configurar vcpkg
set(CMAKE_TOOLCHAIN_FILE "C:/vcpkg/scripts/buildsystems/vcpkg.cmake")

# Encontrar paquetes
find_package(gRPC CONFIG REQUIRED)
find_package(Protobuf CONFIG REQUIRED)

# Archivos fuente
set(SOURCES
    src/worker_main.cpp
    src/worker.cpp
    src/map_task.cpp
    src/reduce_task.cpp
    generated/cpp/worker.pb.cc
    generated/cpp/worker.grpc.pb.cc
)

# Crear ejecutable
add_executable(worker ${SOURCES})

# Incluir directorios
target_include_directories(worker PRIVATE
    src
    generated/cpp
)

# Enlazar librerías
target_link_libraries(worker PRIVATE
    gRPC::grpc++
    gRPC::grpc
    protobuf::libprotobuf
)

# Definir macros de Windows
target_compile_definitions(worker PRIVATE
    _WIN32_WINNT=0x0600
    _WIN32
)

