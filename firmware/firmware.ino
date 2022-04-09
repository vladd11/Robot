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
  if(isForwarding) return;
  isForwarding = true;
  digitalWrite(IN1, LOW);

  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);

  digitalWrite(IN4, LOW);

  delay(250);
  analogWrite(ENA, 75);
  analogWrite(ENB, 75);

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
  analogWrite(ENA, 100);
  analogWrite(ENB, 100);
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

#ifdef BLUETOOTH
enum action {LEFT='l', RIGHT='r', FORWARD='f', BACK='b', STOP='s'};
#else
enum action {LEFT=0x0, RIGHT=0x1, FORWARD=0x2, BACK=0x3, STOP=0x4};
#endif

void loop() {
  //Serial.write(0xC);
  while (Serial.available()) {
    #ifdef BLUETOOTH
    char val = Serial.read();
    #else
    byte val = Serial.read();
    #endif
    switch (val) {
      case LEFT:
        Left();
        break;
      case RIGHT:
        Right();
        break;
      case FORWARD:
        Forward();
        Serial.write(val);
        break;
      case BACK:
        Back();
        break;
      case STOP:
        Stop();
        break;
      default:
        //Stop();
        break;
    }
  }
}
