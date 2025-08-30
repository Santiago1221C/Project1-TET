FROM python:3.12-slim

COPY ./serv_persistor ./home/app

WORKDIR /home/app

RUN pip install --upgrade pip
RUN pip install -r requirements.txt

CMD ["python", "-u", "main.py"]