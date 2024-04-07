package com.company;

import jdk.internal.org.objectweb.asm.tree.analysis.Value;

import java.math.BigInteger;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        int is_pri[] = new int[10007];
        int pri[] = new int[10007];
        for(int i=2;i<=10000;i++)is_pri[i] = 1;
        int cnt = 0;
        for (int i = 2; i <= 10000; i++) {
            if (is_pri[i]==1) {
                pri[++cnt] = i;
            }
            for (int j = 1; j <= cnt && pri[j]*i <= 10000; j++) {
                is_pri[i * pri[j]] = 0;
                if (i % pri[j] == 0) {
                    break;
                }
            }
        }
//        for(int i=1;i<=cnt;i++){
//            System.out.println(pri[i]);
//        }
        Scanner scan = new Scanner(System.in);
        int t;
        t=scan.nextInt();
        for(int i=1;i<=t;i++){
            BigInteger x,fz,fm;
            fz=BigInteger.ONE;
            fm=BigInteger.ONE;
            x=scan.nextBigInteger();
            int j=1;
            for(j=1;j<=cnt;j++){
                BigInteger temp = BigInteger.valueOf(pri[j]);
                fz = fz.multiply(temp);
                temp = temp.add(BigInteger.ONE);
                fm = fm.multiply(temp);
                if(fz.compareTo(x) > 0)break;
//                System.out.print(fz);
//                System.out.print("/");
//                System.out.println(fm);
            }
            fz = fz.divide(BigInteger.valueOf(pri[j]));
            fm = fm.divide(BigInteger.valueOf(pri[j]+1));
            BigInteger temp = fz.gcd(fm);
            fz = fz.divide(temp);
            fm = fm.divide(temp);
            System.out.print(fz);
            System.out.print("/");
            System.out.println(fm);
        }
    }
}
