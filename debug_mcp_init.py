#!/usr/bin/env python3
import json
import subprocess
import sys
import time

def send_message(process, message):
    """Send a JSON message to the process"""
    msg_str = json.dumps(message)
    print(f"SENDING: {msg_str}", file=sys.stderr)
    process.stdin.write(msg_str + '\n')
    process.stdin.flush()

def read_response(process):
    """Read a JSON response from the process"""
    try:
        line = process.stdout.readline()
        if line:
            response = json.loads(line.strip())
            print(f"RECEIVED: {json.dumps(response)}", file=sys.stderr)
            return response
        return None
    except Exception as e:
        print(f"ERROR reading response: {e}", file=sys.stderr)
        return None

def main():
    # Start the Python MCP server
    cmd = ["D:\\MCP\\MCP-workspace-bootcampToProd\\filesystem_mcp_server\\venv\\Scripts\\python", "../filesystem_mcp_server/src/main.py"]
    print(f"Starting process: {' '.join(cmd)}", file=sys.stderr)
    
    process = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=0
    )
    
    time.sleep(2)  # Give server time to start
    
    try:
        # Step 1: Send initialize request
        init_request = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "roots": {"listChanged": True},
                    "sampling": {}
                },
                "clientInfo": {
                    "name": "debug-client",
                    "version": "1.0.0"
                }
            }
        }
        
        send_message(process, init_request)
        
        # Read initialize response
        init_response = read_response(process)
        if init_response and init_response.get("result"):
            print("INITIALIZE successful!", file=sys.stderr)
        else:
            print(f"INITIALIZE failed: {init_response}", file=sys.stderr)
            return
        
        # Step 2: Send initialized notification
        init_notification = {
            "jsonrpc": "2.0",
            "method": "notifications/initialized",
            "params": {}
        }
        
        send_message(process, init_notification)
        print("INITIALIZED notification sent!", file=sys.stderr)
        
        # Wait a moment
        time.sleep(1)
        
        # Step 3: Try to list tools
        tools_request = {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {}
        }
        
        send_message(process, tools_request)
        
        # Read tools response
        tools_response = read_response(process)
        if tools_response and tools_response.get("result"):
            print("TOOLS/LIST successful!", file=sys.stderr)
            tools = tools_response["result"].get("tools", [])
            print(f"Found {len(tools)} tools", file=sys.stderr)
        else:
            print(f"TOOLS/LIST failed: {tools_response}", file=sys.stderr)
        
    except Exception as e:
        print(f"Error during test: {e}", file=sys.stderr)
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
        
        # Read any stderr output
        stderr_output = process.stderr.read()
        if stderr_output:
            print(f"STDERR: {stderr_output}", file=sys.stderr)

if __name__ == "__main__":
    main()
