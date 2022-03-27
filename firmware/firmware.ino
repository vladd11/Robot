#define IN1 A0
#define IN2 A1

#define IN3 A2
#define IN4 A3

#define ENA 5
#define ENB 6

#define DL 7
#define DR 8

#define BLUETOOTH

void setup() {
  Serial.begin(9600);

  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  pinMode(ENA, OUTPUT);
  pinMode(ENB, OUTPUT);
  pinMode(DL, INPUT);
  pinMode(DR, INPUT);

  digitalWrite(ENA, HIGH);
  digitalWrite(ENB, HIGH);

  Serial.setTimeout(500);
}

void Back() {
  digitalWrite(ENA, HIGH);
  digitalWrite(ENB, HIGH);
  digitalWrite(IN1, LOW);

  digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH);

  digitalWrite(IN4, LOW);
}

bool isForwarding = false;

void Forward() {
  if (isForwarding) return;
  isForwarding = true;
  digitalWrite(IN1, LOW);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);

  digitalWrite(IN4, LOW);

  delay(250);
  digitalWrite(ENA, HIGH);
  digitalWrite(ENB, HIGH);

  digitalWrite(IN1, HIGH);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH);

  digitalWrite(IN4, LOW);
}

void Stop() {
  isForwarding = false;
  digitalWrite(IN1, LOW);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);

  digitalWrite(IN4, LOW);
}

void PrepareForTurn() {
  isForwarding = false;
  analogWrite(ENA, 200);
  analogWrite(ENB, 200);
}

void Right() {
  PrepareForTurn();
  digitalWrite(IN1, HIGH);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);

  digitalWrite(IN4, LOW);
}

void Left() {
  PrepareForTurn();
  digitalWrite(IN1, LOW);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);

  digitalWrite(IN4, HIGH);
}

int current = 0;
int target = 0;
bool isStopped = true;

void loop() {
  //Forward();
  //sensor.read();
  
  /*int diff = target - heading;

  if(abs(diff) > 40) {
    if(diff < 0) {
     Left(); 
    } else Right();
  } else if(!isStopped) Forward();
  else Stop();
  delay(500);
  //Serial.setTimeout(2147483646);

  if (Serial.available() > 0) {
    if (Serial.read() == 'r') {
      target = Serial.parseInt();

      Serial.println(target, DEC);
      isStopped = false;
    } else {
      isStopped = true;
      Stop();
    }
  }

  if (current < target) {
    Left();
  } else if (current > target) {
    Right();
  } else if (!isStopped) {
    Forward();
  }

  /*if(!(digitalRead(DL) || digitalRead(DR))) {
    Serial.println('b'); // stuck
    delay(1000);
    }*/
}
