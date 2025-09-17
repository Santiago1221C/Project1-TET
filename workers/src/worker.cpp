#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <map>
#include <sstream>
#include <algorithm>
#include <ctime>
#include <grpcpp/grpcpp.h>
#include "../generated/cpp/worker.grpc.pb.h"

using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::Status;

// Clase principal del Worker
class GridMRWorker final : public worker::WorkerService::Service {
private:
    std::string worker_id;
    std::string nfs_path;
    
public:
    GridMRWorker(std::string id, std::string nfs) : worker_id(id), nfs_path(nfs) {}
    
    // Ejecutar tarea MAP
    Status ProcessMap(ServerContext* context, 
                    const worker::MapRequest* request,
                    worker::MapResponse* response) override {
        
        std::cout << "Ejecutando Map Task: " << request->task_id() << std::endl;
        
        try {
            if (request->function_name() == "wordcount") {
                execute_wordcount_map(request->task_id(), request->input_file());
            } else if (request->function_name() == "sort") {
                execute_sort_map(request->task_id(), request->input_file());
            }
            
            response->set_task_id(request->task_id());
            response->set_worker_id(worker_id);
            response->set_output_file("intermediate/" + request->task_id() + ".txt");
            response->set_status("completed");
            response->set_processing_time(0);
            
        } catch (const std::exception& e) {
            response->set_status("error");
            response->set_error_message("Error: " + std::string(e.what()));
        }
        
        return Status::OK;
    }
    
    // Ejecutar tarea REDUCE
    Status ProcessReduce(ServerContext* context,
                        const worker::ReduceRequest* request,
                        worker::ReduceResponse* response) override {
        
        std::cout << "Ejecutando Reduce Task: " << request->task_id() << std::endl;
        
        try {
            std::vector<std::string> input_files;
            for (int i = 0; i < request->input_files_size(); i++) {
                input_files.push_back(request->input_files(i));
            }
            
            if (request->function_name() == "wordcount") {
                execute_wordcount_reduce(request->task_id(), input_files, "output/" + request->task_id() + "_final.txt");
            } else if (request->function_name() == "sort") {
                execute_sort_reduce(request->task_id(), input_files, "output/" + request->task_id() + "_final.txt");
            }
            
            response->set_task_id(request->task_id());
            response->set_worker_id(worker_id);
            response->set_output_file("output/" + request->task_id() + "_final.txt");
            response->set_status("completed");
            response->set_processing_time(0);
            
        } catch (const std::exception& e) {
            response->set_status("error");
            response->set_error_message("Error: " + std::string(e.what()));
        }
        
        return Status::OK;
    }
    
    // Health check
    Status CheckHealth(ServerContext* context,
                    const worker::HealthCheckRequest* request,
                    worker::HealthCheckResponse* response) override {
        
        response->set_worker_id(worker_id);
        response->set_status("healthy");
        response->set_timestamp(std::time(nullptr));
        
        return Status::OK;
    }

private:
    // ===== FUNCIONES MAP =====
    
    void execute_wordcount_map(const std::string& task_id, const std::string& input_file) {
        std::map<std::string, int> word_count;
        std::ifstream file(nfs_path + "/" + input_file);
        std::string line, word;
        
        // Contar palabras
        while (std::getline(file, line)) {
            std::istringstream iss(line);
            while (iss >> word) {
                // Limpiar y contar
                std::transform(word.begin(), word.end(), word.begin(), ::tolower);
                word_count[word]++;
            }
        }
        
        // Guardar resultado intermedio
        std::ofstream output(nfs_path + "/intermediate/" + task_id + ".txt");
        for (const auto& pair : word_count) {
            output << pair.first << "\t" << pair.second << std::endl;
        }
        
        std::cout << "WordCount Map completado: " << word_count.size() << " palabras únicas" << std::endl;
    }
    
    void execute_sort_map(const std::string& task_id, const std::string& input_file) {
        std::vector<std::string> lines;
        std::ifstream file(nfs_path + "/" + input_file);
        std::string line;
        
        // Leer todas las líneas
        while (std::getline(file, line)) {
            lines.push_back(line);
        }
        
        // Ordenar
        std::sort(lines.begin(), lines.end());
        
        // Guardar resultado intermedio
        std::ofstream output(nfs_path + "/intermediate/" + task_id + ".txt");
        for (const std::string& sorted_line : lines) {
            output << sorted_line << std::endl;
        }
        
        std::cout << "Sort Map completado: " << lines.size() << " líneas ordenadas" << std::endl;
    }
    
    // ===== FUNCIONES REDUCE =====
    
    void execute_wordcount_reduce(const std::string& task_id, 
                                const std::vector<std::string>& input_files,
                                const std::string& output_file) {
        std::map<std::string, int> final_count;
        
        // Procesar todos los archivos intermedios
        for (const std::string& file : input_files) {
            std::ifstream input(nfs_path + "/" + file);
            std::string word;
            int count;
            
            while (input >> word >> count) {
                final_count[word] += count;
            }
        }
        
        // Guardar resultado final
        std::ofstream output(nfs_path + "/" + output_file);
        for (const auto& pair : final_count) {
            output << pair.first << "\t" << pair.second << std::endl;
        }
        
        std::cout << "WordCount Reduce completado: " << final_count.size() << " palabras finales" << std::endl;
    }
    
    void execute_sort_reduce(const std::string& task_id,
                            const std::vector<std::string>& input_files,
                            const std::string& output_file) {
        std::vector<std::string> all_lines;
        
        // Leer todos los archivos intermedios
        for (const std::string& file : input_files) {
            std::ifstream input(nfs_path + "/" + file);
            std::string line;
            
            while (std::getline(input, line)) {
                all_lines.push_back(line);
            }
        }
        
        // Ordenar todo
        std::sort(all_lines.begin(), all_lines.end());
        
        // Guardar resultado final
        std::ofstream output(nfs_path + "/" + output_file);
        for (const std::string& line : all_lines) {
            output << line << std::endl;
        }
        
        std::cout << "Sort Reduce completado: " << all_lines.size() << " líneas finales" << std::endl;
    }
};

// ===== FUNCIÓN PRINCIPAL =====
int main(int argc, char** argv) {
    // Configuración desde argumentos o variables de entorno
    std::string worker_id = (argc > 1) ? argv[1] : "worker-001";
    std::string worker_port = (argc > 2) ? argv[2] : "9090";
    std::string nfs_path = (argc > 3) ? argv[3] : "/mnt/gridmr_nfs";
    
    std::cout << "=== GridMR Worker ===" << std::endl;
    std::cout << "Worker ID: " << worker_id << std::endl;
    std::cout << "Puerto: " << worker_port << std::endl;
    std::cout << "NFS Path: " << nfs_path << std::endl;
    
    // Crear directorios necesarios
    system(("mkdir -p " + nfs_path + "/intermediate").c_str());
    system(("mkdir -p " + nfs_path + "/output").c_str());
    
    // Crear y configurar servidor gRPC
    GridMRWorker worker(worker_id, nfs_path);
    
    std::string server_address = "0.0.0.0:" + worker_port;
    ServerBuilder builder;
    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&worker);
    
    std::unique_ptr<Server> server(builder.BuildAndStart());
    std::cout << "Worker escuchando en " << server_address << std::endl;
    std::cout << "Presiona Ctrl+C para detener..." << std::endl;
    
    // Esperar conexiones
    server->Wait();
    
    return 0;
}