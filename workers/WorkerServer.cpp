#include <iostream>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include <fstream>
#include <algorithm>
#include <chrono>
#include <map>
#include <sstream>
#include <filesystem>
#include <mutex>

// gRPC includes
#include <grpcpp/grpcpp.h>
#include <grpcpp/health_check_service_interface.h>
#include <grpcpp/ext/proto_server_reflection_plugin.h>

// Generated proto includes
#include "../generated/cpp/worker.pb.h"
#include "../generated/cpp/worker.grpc.pb.h"

using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::Status;

using worker::WorkerService;
using worker::MapRequest;
using worker::MapResponse;
using worker::ReduceRequest;
using worker::ReduceResponse;
using worker::HealthCheckRequest;
using worker::HealthCheckResponse;

// =================== MAP COMPONENT ===================
class MapProcessor {
private:
    std::string worker_id;
    std::string nfs_path;
    
public:
    MapProcessor(const std::string& id, const std::string& nfs) 
        : worker_id(id), nfs_path(nfs) {}
    
    std::map<std::string, int> processWordCount(const std::vector<std::string>& input_data) {
        std::map<std::string, int> word_count;
        
        for (const auto& line : input_data) {
            std::istringstream iss(line);
            std::string word;
            
            while (iss >> word) {
                // Limpiar palabra (remover puntuación)
                word.erase(std::remove_if(word.begin(), word.end(), 
                    [](char c) { return !std::isalnum(c); }), word.end());
                
                if (!word.empty()) {
                    std::transform(word.begin(), word.end(), word.begin(), ::tolower);
                    word_count[word]++;
                }
            }
        }
        
        return word_count;
    }
    
    std::vector<std::string> processSort(const std::vector<std::string>& input_data) {
        std::vector<std::string> sorted_data = input_data;
        std::sort(sorted_data.begin(), sorted_data.end());
        return sorted_data;
    }
    
    std::vector<std::string> processGrep(const std::vector<std::string>& input_data, 
                                        const std::string& pattern) {
        std::vector<std::string> filtered_data;
        
        for (const auto& line : input_data) {
            if (line.find(pattern) != std::string::npos) {
                filtered_data.push_back(line);
            }
        }
        
        return filtered_data;
    }
};

// =================== REDUCE COMPONENT ===================
class ReduceProcessor {
private:
    std::string worker_id;
    std::string nfs_path;
    
public:
    ReduceProcessor(const std::string& id, const std::string& nfs) 
        : worker_id(id), nfs_path(nfs) {}
    
    std::map<std::string, int> reduceWordCount(const std::vector<std::string>& intermediate_files) {
        std::map<std::string, int> final_count;
        
        for (const auto& file_path : intermediate_files) {
            std::string full_path = nfs_path + "/" + file_path;
            std::ifstream file(full_path);
            std::string line;
            
            while (std::getline(file, line)) {
                size_t tab_pos = line.find('\t');
                if (tab_pos != std::string::npos) {
                    std::string word = line.substr(0, tab_pos);
                    int count = std::stoi(line.substr(tab_pos + 1));
                    final_count[word] += count;
                }
            }
            file.close();
        }
        
        return final_count;
    }
    
    std::vector<std::string> reduceSort(const std::vector<std::string>& intermediate_files) {
        std::vector<std::string> all_lines;
        
        for (const auto& file_path : intermediate_files) {
            std::string full_path = nfs_path + "/" + file_path;
            std::ifstream file(full_path);
            std::string line;
            
            while (std::getline(file, line)) {
                all_lines.push_back(line);
            }
            file.close();
        }
        
        std::sort(all_lines.begin(), all_lines.end());
        return all_lines;
    }
    
    std::vector<std::string> reduceGrep(const std::vector<std::string>& intermediate_files) {
        std::vector<std::string> all_matches;
        
        for (const auto& file_path : intermediate_files) {
            std::string full_path = nfs_path + "/" + file_path;
            std::ifstream file(full_path);
            std::string line;
            
            while (std::getline(file, line)) {
                all_matches.push_back(line);
            }
            file.close();
        }
        
        return all_matches;
    }
};

// =================== TASK EXECUTOR COMPONENT ===================
class TaskExecutor {
private:
    std::string worker_id;
    std::string nfs_path;
    std::unique_ptr<MapProcessor> map_processor;
    std::unique_ptr<ReduceProcessor> reduce_processor;
    std::mutex execution_mutex;
    
public:
    TaskExecutor(const std::string& id, const std::string& nfs) 
        : worker_id(id), nfs_path(nfs) {
        map_processor = std::make_unique<MapProcessor>(worker_id, nfs_path);
        reduce_processor = std::make_unique<ReduceProcessor>(worker_id, nfs_path);
        
        // Crear directorios si no existen
        std::filesystem::create_directories(nfs_path + "/input");
        std::filesystem::create_directories(nfs_path + "/intermediate");
        std::filesystem::create_directories(nfs_path + "/output");
    }
    
    std::string executeMapTask(const MapRequest* request) {
        std::lock_guard<std::mutex> lock(execution_mutex);
        
        auto start_time = std::chrono::high_resolution_clock::now();
        
        std::cout << "[TaskExecutor] Worker " << worker_id 
                  << " executing MAP task: " << request->task_id() << std::endl;
        
        // Leer archivo de entrada
        std::string input_file = nfs_path + "/" + request->input_file();
        std::ifstream file(input_file);
        std::vector<std::string> input_data;
        std::string line;
        
        while (std::getline(file, line)) {
            input_data.push_back(line);
        }
        file.close();
        
        std::cout << "[TaskExecutor] Read " << input_data.size() << " lines from " << input_file << std::endl;
        
        // Obtener función Map y parámetros
        std::string function_name = request->function_name();
        auto parameters = request->parameters();
        
        // Crear archivo de salida intermedio
        std::string output_file = "intermediate/" + request->task_id() + "_" + worker_id + ".txt";
        std::string full_output_path = nfs_path + "/" + output_file;
        std::ofstream out_file(full_output_path);
        
        // Ejecutar función Map correspondiente
        if (function_name == "wordcount") {
            auto word_count = map_processor->processWordCount(input_data);
            for (const auto& pair : word_count) {
                out_file << pair.first << "\t" << pair.second << std::endl;
            }
            std::cout << "[TaskExecutor] Processed " << word_count.size() << " unique words" << std::endl;
            
        } else if (function_name == "sort") {
            auto sorted_data = map_processor->processSort(input_data);
            for (const auto& line : sorted_data) {
                out_file << line << std::endl;
            }
            std::cout << "[TaskExecutor] Sorted " << sorted_data.size() << " lines" << std::endl;
            
        } else if (function_name == "grep") {
            std::string pattern = parameters.count("pattern") ? parameters.at("pattern") : "";
            auto filtered_data = map_processor->processGrep(input_data, pattern);
            for (const auto& line : filtered_data) {
                out_file << line << std::endl;
            }
            std::cout << "[TaskExecutor] Found " << filtered_data.size() << " matching lines" << std::endl;
        }
        
        out_file.close();
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        
        std::cout << "[TaskExecutor] MAP task completed in " << duration.count() << "ms" << std::endl;
        
        return output_file;
    }
    
    std::string executeReduceTask(const ReduceRequest* request) {
        std::lock_guard<std::mutex> lock(execution_mutex);
        
        auto start_time = std::chrono::high_resolution_clock::now();
        
        std::cout << "[TaskExecutor] Worker " << worker_id 
                  << " executing REDUCE task: " << request->task_id() << std::endl;
        
        // Obtener función Reduce
        std::string function_name = request->function_name();
        std::vector<std::string> input_files;
        for (const auto& file : request->input_files()) {
            input_files.push_back(file);
        }
        
        std::cout << "[TaskExecutor] Processing " << input_files.size() << " intermediate files" << std::endl;
        
        // Crear archivo de salida final
        std::string output_file = "output/" + request->task_id() + "_final_" + worker_id + ".txt";
        std::string full_output_path = nfs_path + "/" + output_file;
        std::ofstream out_file(full_output_path);
        
        // Ejecutar función Reduce correspondiente
        if (function_name == "wordcount") {
            auto final_count = reduce_processor->reduceWordCount(input_files);
            for (const auto& pair : final_count) {
                out_file << pair.first << "\t" << pair.second << std::endl;
            }
            std::cout << "[TaskExecutor] Reduced to " << final_count.size() << " unique words" << std::endl;
            
        } else if (function_name == "sort") {
            auto sorted_lines = reduce_processor->reduceSort(input_files);
            for (const auto& line : sorted_lines) {
                out_file << line << std::endl;
            }
            std::cout << "[TaskExecutor] Final sorted output: " << sorted_lines.size() << " lines" << std::endl;
            
        } else if (function_name == "grep") {
            auto all_matches = reduce_processor->reduceGrep(input_files);
            for (const auto& line : all_matches) {
                out_file << line << std::endl;
            }
            std::cout << "[TaskExecutor] Final grep results: " << all_matches.size() << " matches" << std::endl;
        }
        
        out_file.close();
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        
        std::cout << "[TaskExecutor] REDUCE task completed in " << duration.count() << "ms" << std::endl;
        
        return output_file;
    }
};

// =================== WORKER SERVICE IMPLEMENTATION ===================
class WorkerServiceImpl final : public WorkerService::Service {
private:
    std::string worker_id;
    std::string nfs_path;
    std::unique_ptr<TaskExecutor> task_executor;
    
public:
    WorkerServiceImpl(const std::string& id, const std::string& nfs) 
        : worker_id(id), nfs_path(nfs) {
        task_executor = std::make_unique<TaskExecutor>(worker_id, nfs_path);
        std::cout << "[WorkerService] Initialized worker " << worker_id 
                  << " with NFS path: " << nfs_path << std::endl;
    }

    Status ProcessMap(ServerContext* context, const MapRequest* request,
                      MapResponse* response) override {
        std::cout << "\n[WorkerService] ========== MAP REQUEST ==========" << std::endl;
        std::cout << "[WorkerService] Task ID: " << request->task_id() << std::endl;
        std::cout << "[WorkerService] Function: " << request->function_name() << std::endl;
        std::cout << "[WorkerService] Input File: " << request->input_file() << std::endl;
        
        try {
            auto start_time = std::chrono::high_resolution_clock::now();
            
            std::string output_file = task_executor->executeMapTask(request);
            
            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
            
            response->set_task_id(request->task_id());
            response->set_worker_id(worker_id);
            response->set_output_file(output_file);
            response->set_status("SUCCESS");
            response->set_processing_time(duration.count());
            
            std::cout << "[WorkerService] MAP task SUCCESS - Output: " << output_file << std::endl;
            std::cout << "[WorkerService] ================================\n" << std::endl;
            
            return Status::OK;
            
        } catch (const std::exception& e) {
            std::cout << "[WorkerService] MAP task ERROR: " << e.what() << std::endl;
            response->set_status("ERROR");
            response->set_error_message(e.what());
            return Status::INTERNAL;
        }
    }

    Status ProcessReduce(ServerContext* context, const ReduceRequest* request,
                         ReduceResponse* response) override {
        std::cout << "\n[WorkerService] ========== REDUCE REQUEST ==========" << std::endl;
        std::cout << "[WorkerService] Task ID: " << request->task_id() << std::endl;
        std::cout << "[WorkerService] Function: " << request->function_name() << std::endl;
        std::cout << "[WorkerService] Input Files: " << request->input_files_size() << std::endl;
        
        try {
            auto start_time = std::chrono::high_resolution_clock::now();
            
            std::string output_file = task_executor->executeReduceTask(request);
            
            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
            
            response->set_task_id(request->task_id());
            response->set_worker_id(worker_id);
            response->set_output_file(output_file);
            response->set_status("SUCCESS");
            response->set_processing_time(duration.count());
            
            std::cout << "[WorkerService] REDUCE task SUCCESS - Output: " << output_file << std::endl;
            std::cout << "[WorkerService] ==================================\n" << std::endl;
            
            return Status::OK;
            
        } catch (const std::exception& e) {
            std::cout << "[WorkerService] REDUCE task ERROR: " << e.what() << std::endl;
            response->set_status("ERROR");
            response->set_error_message(e.what());
            return Status::INTERNAL;
        }
    }

    Status CheckHealth(ServerContext* context, const HealthCheckRequest* request,
                       HealthCheckResponse* response) override {
        response->set_worker_id(worker_id);
        response->set_status("HEALTHY");
        response->set_timestamp(
            std::chrono::duration_cast<std::chrono::seconds>(
                std::chrono::system_clock::now().time_since_epoch()
            ).count()
        );
        
        // Agregar métricas del worker
        auto metrics = response->mutable_metrics();
        (*metrics)["nfs_path"] = nfs_path;
        (*metrics)["status"] = "ACTIVE";
        
        return Status::OK;
    }
};

void RunServer(const std::string& server_address, const std::string& worker_id, 
               const std::string& nfs_path) {
    WorkerServiceImpl service(worker_id, nfs_path);

    grpc::EnableDefaultHealthCheckService(true);
    grpc::reflection::InitProtoReflectionServerBuilderPlugin();
    
    ServerBuilder builder;
    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    
    std::unique_ptr<Server> server(builder.BuildAndStart());
    
    std::cout << "\n=================== GRIDMR WORKER ===================" << std::endl;
    std::cout << "Worker ID: " << worker_id << std::endl;
    std::cout << "Server Address: " << server_address << std::endl;
    std::cout << "NFS Path: " << nfs_path << std::endl;
    std::cout << "Status: RUNNING" << std::endl;
    std::cout << "Components: [MapProcessor] [ReduceProcessor] [TaskExecutor]" << std::endl;
    std::cout << "=====================================================" << std::endl;
    
    server->Wait();
}

int main(int argc, char** argv) {
    std::string worker_id = "worker_1";
    std::string server_port = "50051";
    std::string nfs_path = "./shared/nfs_shared";
    
    // Parsear argumentos de línea de comandos
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--id" && i + 1 < argc) {
            worker_id = argv[++i];
        } else if (arg == "--port" && i + 1 < argc) {
            server_port = argv[++i];
        } else if (arg == "--nfs" && i + 1 < argc) {
            nfs_path = argv[++i];
        }
    }
    
    std::string server_address = "0.0.0.0:" + server_port;
    
    std::cout << "Starting GridMR Worker Server..." << std::endl;
    std::cout << "Initializing components: Map, Reduce, TaskExecutor..." << std::endl;
    
    RunServer(server_address, worker_id, nfs_path);
    
    return 0;
}