#!/usr/bin/env python3
"""
代码行数统计工具
统计项目中的代码、注释和空行数量
"""

import os
import argparse
from pathlib import Path


def count_lines_in_file(file_path):
    """统计单个文件的行数：代码行、注释行、空行"""
    code_lines = 0
    comment_lines = 0
    empty_lines = 0
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            
        for line in lines:
            stripped = line.strip()
            
            if not stripped:
                empty_lines += 1
            elif stripped.startswith('#') or stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*') or stripped.startswith('*/') or stripped.startswith('--'):
                comment_lines += 1
            else:
                code_lines += 1
                
    except UnicodeDecodeError:
        # 如果是二进制文件，跳过
        pass
    except Exception:
        # 其他错误也跳过
        pass
        
    return code_lines, comment_lines, empty_lines


def is_source_file(file_path):
    """判断是否为源代码文件"""
    exts = {
        '.java', '.js', '.ts', '.jsx', '.tsx', '.py', '.go', '.cpp', '.c', '.h', 
        '.cs', '.rb', '.php', '.html', '.css', '.vue', '.sql', '.sh', '.xml', 
        '.kt', '.scala', '.dart', '.swift', '.m', '.mm'
    }
    
    # 定义构建/配置文件名称模式，这些不应该计入源代码
    build_config_patterns = [
        'build.gradle', 'settings.gradle', 'gradle.properties',
        'pom.xml', 'package.json', 'package-lock.json', 
        'requirements.txt', 'Dockerfile', 'docker-compose.yml',
        'webpack.config.js', 'vite.config.js', 'gulpfile.js', 'gruntfile.js'
    ]
    
    path_obj = Path(file_path)
    file_name = path_obj.name.lower()
    
    # 检查是否为构建/配置文件
    for pattern in build_config_patterns:
        if file_name.startswith(pattern.split('.')[0]) or file_name == pattern:
            return False
    
    # 检查扩展名
    return path_obj.suffix.lower() in exts and path_obj.suffix.lower() != '.kt'


def count_lines_recursive(root_path, ignore_dirs=None):
    """递归统计目录下所有源代码文件的行数"""
    if ignore_dirs is None:
        ignore_dirs = {'.git', '__pycache__', 'node_modules', 'dist', '.vscode', '.gradle', 'build'}
    
    total_files = 0
    total_code_lines = 0
    total_comment_lines = 0
    total_empty_lines = 0
    
    file_ext_stats = {}
    
    for root, dirs, files in os.walk(root_path):
        # 过滤掉忽略的目录
        dirs[:] = [d for d in dirs if d not in ignore_dirs]
        
        for file in files:
            file_path = os.path.join(root, file)
            
            if is_source_file(file_path):
                code, comment, empty = count_lines_in_file(file_path)
                
                # 累计总数
                total_files += 1
                total_code_lines += code
                total_comment_lines += comment
                total_empty_lines += empty
                
                # 按扩展名统计
                ext = Path(file_path).suffix.lower()
                if ext not in file_ext_stats:
                    file_ext_stats[ext] = {'files': 0, 'code': 0, 'comment': 0, 'empty': 0}
                
                file_ext_stats[ext]['files'] += 1
                file_ext_stats[ext]['code'] += code
                file_ext_stats[ext]['comment'] += comment
                file_ext_stats[ext]['empty'] += empty
    
    return {
        'total_files': total_files,
        'total_code_lines': total_code_lines,
        'total_comment_lines': total_comment_lines,
        'total_empty_lines': total_empty_lines,
        'file_ext_stats': file_ext_stats
    }


def main():
    parser = argparse.ArgumentParser(description='统计项目代码行数')
    parser.add_argument('path', nargs='?', default='..', help='要统计的项目路径 (默认: ..)')
    parser.add_argument('--details', '-d', action='store_true', help='显示各类型文件的详细统计信息')
    
    args = parser.parse_args()
    
    print(f"正在统计路径: {args.path}")
    print("="*50)
    
    stats = count_lines_recursive(args.path)
    
    print(f"总文件数: {stats['total_files']}")
    print(f"代码行数: {stats['total_code_lines']}")
    print(f"注释行数: {stats['total_comment_lines']}")
    print(f"空行行数: {stats['total_empty_lines']}")
    print(f"总行数: {stats['total_code_lines'] + stats['total_comment_lines'] + stats['total_empty_lines']}")
    
    if args.details and stats['file_ext_stats']:
        print("\n按文件类型统计 (不含构建配置文件):")
        print("-"*50)
        print(f"{'扩展名':<10} {'文件数':<8} {'代码行':<10} {'注释行':<10} {'空行':<10}")
        print("-" * 50)
        for ext, stat in sorted(stats['file_ext_stats'].items()):
            print(f"{ext:<10} {stat['files']:<8} {stat['code']:<10} {stat['comment']:<10} {stat['empty']:<10}")


if __name__ == "__main__":
    main()