import grpc
import client_communication_pb2
import client_communication_pb2_grpc
from concurrent import futures

def main():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    client_communication_pb2_grpc.add_ClientCommunicationServicer_to_server(client_communication_pb2_grpc.ClientCommunication(), server)
    
    server.add_insecure_port('[::]:50055')
    server.start()
    
    print("Server started on port 50055")
if __name__ == "__main__":
    main()