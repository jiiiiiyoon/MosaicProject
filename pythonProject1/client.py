import socketio
import base64

sio = socketio.Client()

@sio.event
def connect():
    print('connection established')

@sio.on('my message')
def my_message(data):
    sio.emit('test', {'msg': 'clinet response'})
    print("?")
3
@sio.on('add gesture')
def add_gesture(data):
    all, count, frame = data['all'], data['count'], data['frame']
    decodedFrame = base64.b64decode(frame)
    print(decodedFrame)

@sio.event
def disconnect():
    print('disconnected from server')

sio.connect('http://220.69.208.235:8080')

# sio.emit('client registration', {'device': 'ml'})
# # sio.emit('get gesture', {'did': 1, 'gid': 1})
# sio.emit('get gesture', {'did': 1, 'gid': 2})

sio.wait()