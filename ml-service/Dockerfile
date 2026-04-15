FROM python:3.11-slim

WORKDIR /app/ml-service

COPY ml-service/app.py ./app.py
COPY ml-service/safelinkmodel.pkl ./safelinkmodel.pkl
COPY config.json /app/config.json

RUN pip install --no-cache-dir flask joblib scikit-learn

EXPOSE 5000

CMD ["python", "app.py"]
