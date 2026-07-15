#!/usr/bin/env python3
"""
文件树生成工具
根据配置文件生成项目文件树结构
"""

import os
import json
import argparse
from pathlib import Path


def load_config(config_path):
    """加载配置文件"""
    if not os.path.exists(config_path):
        return {
            "project_root": "..",
            "output_file": "file_tree.txt",
            "ignore_dirs": [".git", "__pycache__", "node_modules", "dist", ".vscode"],
            "ignore_files": [".DS_Store"]
        }
    
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_config(config_path, config):
    """保存配置文件"""
    with open(config_path, 'w', encoding='utf-8') as f:
        json.dump(config, f, indent=2, ensure_ascii=False)


def generate_file_tree(root_path, ignore_dirs, ignore_files, prefix=""):
    """递归生成文件树"""
    tree_lines = []
    items = sorted(os.listdir(root_path))
    
    # 过滤掉忽略的目录和文件
    items = [item for item in items 
             if not (os.path.isdir(os.path.join(root_path, item)) and item in ignore_dirs) and
                not (os.path.isfile(os.path.join(root_path, item)) and item in ignore_files)]
    
    for i, item in enumerate(items):
        item_path = os.path.join(root_path, item)
        is_last = i == len(items) - 1
        current_prefix = "└── " if is_last else "├── "
        
        if os.path.isdir(item_path):
            tree_lines.append(f"{prefix}{current_prefix}{item}/")
            extension = "    " if is_last else "│   "
            tree_lines.extend(generate_file_tree(item_path, ignore_dirs, ignore_files, prefix + extension))
        else:
            tree_lines.append(f"{prefix}{current_prefix}{item}")
    
    return tree_lines


def init_config(config_path):
    """初始化配置文件"""
    config = {
        "project_root": "..",
        "output_file": "file_tree.txt",
        "ignore_dirs": [".git", "__pycache__", "node_modules", "dist", ".vscode"],
        "ignore_files": [".DS_Store"]
    }
    save_config(config_path, config)
    print(f"配置文件已创建: {config_path}")


def main():
    parser = argparse.ArgumentParser(description='生成项目文件树')
    parser.add_argument('action', choices=['init', 'generate'], 
                        help='执行操作: init(初始化配置文件) 或 generate(生成文件树)')
    parser.add_argument('--config', default='file_tree_config.json', 
                        help='配置文件路径 (默认: file_tree_config.json)')
    
    args = parser.parse_args()
    
    if args.action == 'init':
        init_config(args.config)
    elif args.action == 'generate':
        config = load_config(args.config)
        project_root = config.get("project_root", "..")
        output_file = config.get("output_file", "file_tree.txt")
        ignore_dirs = config.get("ignore_dirs", [])
        ignore_files = config.get("ignore_files", [])
        
        print(f"正在生成文件树...")
        print(f"项目根目录: {project_root}")
        print(f"忽略目录: {ignore_dirs}")
        print(f"忽略文件: {ignore_files}")
        
        tree_lines = generate_file_tree(project_root, ignore_dirs, ignore_files)
        
        # 添加标题
        project_name = os.path.basename(os.path.abspath(project_root))
        output_lines = [f"{project_name}", "=" * len(project_name), ""]
        output_lines.extend(tree_lines)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines))
        
        print(f"文件树已保存到: {output_file}")


if __name__ == "__main__":
    main()