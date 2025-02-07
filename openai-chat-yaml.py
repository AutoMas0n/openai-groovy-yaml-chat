import sys
import argparse
import json
import threading
import socket
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
import requests
import yaml
from pathlib import Path
import os
import shutil
from datetime import datetime
import re
import warnings
import urllib3
warnings.filterwarnings("ignore", category=urllib3.exceptions.InsecureRequestWarning)

# ---------- Server Implementation ----------
class OpenAIServerHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/sendRequest':
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            data = json.loads(post_data)
            
            # Determine the appropriate API key based on the URL
            url = data.get('URL', '')
            if 'api.deepseek.com' in url:
                api_key = self.server.keys.get('deepseek', '')
            else:  # Default to OpenAI
                api_key = self.server.keys.get('openai', '')

            headers = {
                'Authorization': f'Bearer {api_key}',
                'Content-Type': 'application/json'
            }
            
            try:
                message = data['message']
                response = requests.request(
                    data['reqMethod'],
                    url,
                    headers=headers,
                    json=message,
                    verify=False
                )
                response_content = response.json() if response.content else {}
                response_data = {
                    'rc': response.status_code,
                    'result': response_content
                }
            except Exception as e:
                response_data = {
                    'rc': 500,
                    'result': str(e)
                }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response_data).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')

class OpenAIServer(HTTPServer):
    def __init__(self, server_address, keys):
        self.keys = keys  # Dictionary containing provider keys
        super().__init__(server_address, OpenAIServerHandler)

def run_server(port, keys):
    server = OpenAIServer(('localhost', port), keys)
    print(f"AI Tunnel Server started on port {port}")
    server.serve_forever()

# ---------- Client Implementation ----------
class OpenAIChat:
    def __init__(self, port):
        self.port = port
        self.user_input_file = 'input.txt'
        self.conv_yaml_file = 'input.yaml'
        self.output_file = 'output.md'
        self.auto_clean = True
        self.model_list = [
            "gpt-4o", 
            "gpt-3.5-turbo", 
            "gpt-3.5-turbo-16k", 
            "gpt-4-1106-preview",
            "deepseek-reasoner"
        ]
        self.system_role_init_content = ''
        self.conversation = []
        self.last_conversation = None
        self.temp = 0.6
        self.system_props = self.load_system_properties()

    def load_system_properties(self):
        props = {}
        if not Path('system.properties').exists():
            return props
        
        with open('system.properties', 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                key, value = line.split('=', 1)
                parts = value.split(':', 2)
                if len(parts) != 3:
                    continue
                prompt, model, tokens = parts
                props[key] = {
                    'prompt': prompt,
                    'model': int(model),
                    'max_tokens': int(tokens)
                }
        return props

    def check_for_server(self):
        start_time = time.time()
        timeout = 30  # seconds
        while True:
            try:
                with socket.create_connection(('localhost', self.port), timeout=1):
                    print(f"Server up and running on port {self.port}")
                    return
            except (ConnectionRefusedError, socket.timeout):
                if time.time() - start_time > timeout:
                    raise Exception(f"Server did not start within {timeout} seconds")
                print(f"Waiting for server on port {self.port}...")
                time.sleep(1)

    def start_listener(self):
        self.check_for_server()
        while True:
            try:
                user_input = input("Press Enter to submit the input file or enter a command: ")
                self.process_input(user_input)
            except KeyboardInterrupt:
                print("\nExiting...")
                break

    def process_input(self, user_input):
        model_choice = 1
        max_tokens = 3200
        system_content = ''

        if user_input in self.system_props:
            prop = self.system_props[user_input]
            system_content = prop['prompt']
            model_choice = prop['model']
            max_tokens = prop['max_tokens']
        elif '+' in user_input:
            self.temp = min(1.0, self.temp + user_input.count('+') * 0.1)
            self.temp = round(self.temp * 10) / 10
            print(f"Temperature set to {self.temp}")
            return
        elif '-' in user_input:
            self.temp = max(0.0, self.temp - user_input.count('-') * 0.1)
            self.temp = round(self.temp * 10) / 10
            print(f"Temperature set to {self.temp}")
            return
        elif 'clear' in user_input:
            self.clear_conversation()
            return
        elif 'reset' in user_input:
            self.clear_conversation()
            open(self.output_file, 'w').close()
            return
        elif 'autoclean' in user_input:
            self.auto_clean = not self.auto_clean
            print(f"Auto clean set to {self.auto_clean}")
            return
        elif 'backup' in user_input:
            self.create_backup()
            return
        elif 'list' in user_input:
            self.list_models()
            return
        elif 'heading' in user_input:
            self.generate_heading()
            return

        if not system_content:
            system_content = "Answer concisely, precisely, no summaries. Say 's' or 'sry' for apologies and proceed"

        self.process_conversation(system_content, model_choice, max_tokens)

    def process_conversation(self, system_content, model_choice, max_tokens):
        self.load_conversation()
        
        try:
            with open(self.user_input_file, 'r') as f:
                user_input = f.read()
        except FileNotFoundError:
            print("Input file not found")
            return

        if not user_input.strip():
            print("No input in file")
            return

        # Add user's input to the conversation
        self.update_conversation('user', user_input)

        payload = self.create_payload(system_content, model_choice, max_tokens)
        response = self.send_to_server(payload)
        
        if response:
            self.handle_response(response)

    def create_payload(self, system_content, model_choice, max_tokens):
        # Build messages with system and existing conversation (including the new user input)
        messages = [{'role': 'system', 'content': system_content}]
        for entry in self.conversation:
            for role, content in entry.items():
                messages.append({'role': role, 'content': content})
        
        return {
            'model': self.model_list[model_choice],
            'messages': messages,
            'max_tokens': max_tokens,
            'temperature': self.temp
        }


    def send_to_server(self, payload):
        # Determine the appropriate API endpoint based on model name
        model_name = payload.get('model', '').lower()
        if 'deep' in model_name:
            url = 'https://api.deepseek.com/v1/chat/completions'
        else:
            url = 'https://api.openai.com/v1/chat/completions'

        try:
            response = requests.post(
                f'http://localhost:{self.port}/sendRequest',
                json={
                    'reqMethod': 'POST',
                    'URL': url,
                    'message': payload,
                    'failOnError': False,
                    'useProxy': False
                }
            )
            response_data = response.json()
            if 'error' in response_data.get('result', {}):
                print(f"{response_data['rc']} | {response_data['result']['error']['message']}")            
            return response.json().get('result', {})
        except Exception as e:
            print(f"Error contacting server: {e}")
            return None

    def handle_response(self, response):
        try:
            ai_response = response['choices'][0]['message']['content']
        except (KeyError, IndexError):
            print("Invalid response format from API")
            return

        self.update_output(ai_response)
        self.update_conversation('assistant', ai_response)
        self.last_conversation = self.conversation.copy()

    def update_output(self, ai_response):
        header = '\n\n---\n\n' if Path(self.output_file).exists() else '# Conversation\n'
        with open(self.output_file, 'a') as f:
            f.write(f"{header}{ai_response}")

    def load_conversation(self):
        try:
            with open(self.conv_yaml_file, 'r') as f:
                data = yaml.safe_load(f) or {'conversation': []}
            self.conversation = data.get('conversation', [])
        except FileNotFoundError:
            self.conversation = []

    def update_conversation(self, role, content):
        if self.auto_clean:
            content = self.clean_content(content)
        self.conversation.append({role: content})
        self.save_conversation()

    def clean_content(self, text):
        text = re.sub(r'[ \t]+$', '', text, flags=re.MULTILINE)
        text = re.sub(r'\n+', '\n', text).strip()
        return text

    def save_conversation(self):
        with open(self.conv_yaml_file, 'w') as f:
            yaml.dump({'conversation': self.conversation}, f, default_flow_style=False, indent=2)

    def clear_conversation(self):
        self.conversation = []
        self.save_conversation()
        print("Conversation cleared")

    def create_backup(self):
        timestamp = datetime.now().strftime("%d-%m-%Y_%H_%M_%S")
        backup_dir = Path('chats')
        backup_dir.mkdir(exist_ok=True)
        shutil.copy(self.output_file, backup_dir / f'output-{timestamp}.md')
        print("Backup created")

    def list_models(self):
        try:
            response = requests.post(
                f'http://localhost:{self.port}/sendRequest',
                json={
                    'reqMethod': 'GET',
                    'URL': 'https://api.openai.com/v1/models',
                    'message': '',
                    'failOnError': False,
                    'useProxy': False
                }
            )
            print(json.dumps(response.json().get('result', {}), indent=2))
        except Exception as e:
            print(f"Error listing models: {e}")

    def generate_heading(self):
        # Implementation omitted for brevity
        pass

# ---------- Main Function ----------
def main():
    # Parse arguments
    parser = argparse.ArgumentParser()
    parser.add_argument('args', nargs='*', help="Port number and additional arguments")
    args = parser.parse_args().args

    # Handle port
    port = 9001
    if args:
        try:
            port = int(args[0])
            args = args[1:]
        except ValueError:
            pass

    # Handle keys
    def get_key(name):
        for arg in args:
            if arg.startswith(f'{name}:'):
                return arg.split(':', 1)[1]
        try:
            with open(name, 'r') as f:
                return f.read().strip()
        except FileNotFoundError:
            raise Exception(f'Missing {name} - provide as argument or file')

    keys = {
        'openai': get_key('key'),
        'deepseek': get_key('deepkey')
    }

    # Start server with both keys
    server_thread = threading.Thread(target=run_server, args=(port, keys), daemon=True)
    server_thread.start()

    # Start client
    client = OpenAIChat(port)
    client.start_listener()

if __name__ == '__main__':
    main()