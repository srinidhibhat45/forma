export const clamp = (n, min=0, max=100) => Math.min(max, Math.max(min, n));
export const round = n => Math.round(n);
export const localDate = (d = new Date()) => new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(d);
export const duration = minutes => minutes == null ? '—' : `${Math.floor(minutes/60)}h ${Math.round(minutes%60)}m`;
export const humanDate = date => new Date(date+'T12:00:00+05:30').toLocaleDateString('en-IN',{weekday:'long',month:'long',day:'numeric'});
export const FIELDS = {steps:[0,200000],sleepMinutes:[0,1440],sleepEfficiency:[0,100],sleepRegularity:[0,100],restingHeartRate:[20,220],hrv:[1,500],spo2:[50,100],respiratoryRate:[3,60],skinTemperature:[20,45],activeMinutes:[0,1440],activeCalories:[0,20000],distanceKm:[0,500],floors:[0,1000],weightKg:[15,500],bodyFat:[1,80],samsungEnergy:[0,100]};
export function validateImport(input) {
  if (!input || input.version !== 1 || !Array.isArray(input.days) || !input.days.length || input.days.length>3660) throw new Error('Use a Forma v1 JSON file with 1–3,660 daily records. Download the sample to see the format.');
  const seen = new Set();
  const days=input.days.map((day,i) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day.date||'') || Number.isNaN(Date.parse(day.date)) || new Date(day.date).toISOString().slice(0,10)!==day.date) throw new Error(`Record ${i+1} has an invalid date. Use YYYY-MM-DD.`);
    if (seen.has(day.date)) throw new Error(`Duplicate date: ${day.date}. Combine its measurements first.`);
    seen.add(day.date);
    const clean={date:day.date};
    for (const [key,[min,max]] of Object.entries(FIELDS)) {
      if(day[key] == null) continue;
      if(typeof day[key]!=='number' || !Number.isFinite(day[key]) || day[key]<min || day[key]>max) throw new Error(`${key} on ${day.date} must be between ${min} and ${max}.`);
      clean[key]=day[key];
    }
    if(day.sleepStages != null) {
      if(!Array.isArray(day.sleepStages) || day.sleepStages.length>1000) throw new Error('Invalid sleep stages.');
      clean.sleepStages=day.sleepStages.map(s => {
        if(!['Awake','REM','Light','Deep'].includes(s.stage) || typeof s.minutes!=='number' || !Number.isFinite(s.minutes) || s.minutes<=0 || s.minutes>1440) throw new Error('Sleep stages require a valid stage and positive duration in minutes.');
        return {stage:s.stage,minutes:s.minutes};
      });
      const total=clean.sleepStages.reduce((s,x)=>s+x.minutes,0);
      if(total>1440) throw new Error('Sleep stages cannot exceed 24 hours.');
    }
    if(day.energySamples != null) {
      if(!Array.isArray(day.energySamples) || day.energySamples.length>1440) throw new Error('Invalid energy samples.');
      clean.energySamples=day.energySamples.map(s => {
        if(typeof s.hour!=='number' || !Number.isFinite(s.hour) || s.hour<0 || s.hour>24) throw new Error('Energy sample hour must be between 0 and 24.');
        const sample={hour:s.hour};
        for(const [key,max] of [['activeMinutes',60],['restMinutes',60],['steps',15000],['heartRate',240]]) {
          if(s[key] != null) {
            if(typeof s[key]!=='number' || !Number.isFinite(s[key]) || s[key]<0 || s[key]>max) throw new Error(`Invalid ${key} in an energy sample.`);
            sample[key]=s[key];
          }
        }
        if((sample.activeMinutes||0)+(sample.restMinutes||0)>60) throw new Error('Active and rest minutes in an hourly sample cannot exceed 60.');
        return sample;
      }).sort((a,b)=>a.hour-b.hour);
      if(new Set(clean.energySamples.map(s=>s.hour)).size!==clean.energySamples.length) throw new Error('Energy samples must have unique hours.');
    }
    return clean;
  }).sort((a,b)=>a.date.localeCompare(b.date));
  const source=typeof input.source==='string'?input.source.slice(0,120):'Imported file';
  return {version:1,source,importedAt:new Date().toISOString(),days};
}
const mean = arr => arr.length ? arr.reduce((a,b)=>a+b,0)/arr.length : null;
export function baseline(days,date,field) {return mean(days.filter(d=>d.date<date && (Date.parse(date)-Date.parse(d.date))<=28*864e5 && d[field]!=null).map(d=>d[field]));}
export function sleepScore(day,goal=480) {
  if(day?.sleepMinutes==null) return {value:null,confidence:'Unavailable',factors:[]};
  const factors=[{label:'Sleep duration',value:round(clamp(day.sleepMinutes/goal*100)),weight:0.5,detail:`${duration(day.sleepMinutes)} of your ${duration(goal)} goal`}];
  if(day.sleepEfficiency!=null) factors.push({label:'Sleep efficiency',value:day.sleepEfficiency,weight:0.3,detail:`${day.sleepEfficiency}% of time in bed asleep`});
  if(day.sleepRegularity!=null) factors.push({label:'Schedule consistency',value:day.sleepRegularity,weight:0.2,detail:`${day.sleepRegularity}% schedule regularity`});
  const sum=factors.reduce((s,f)=>s+f.weight,0);
  return {value:round(factors.reduce((s,f)=>s+f.value*f.weight,0)/sum),confidence:factors.length===3?'Moderate':'Limited',factors};
}
export function recoveryScore(day,days,goal=480) {
  if(!day) return {value:null,confidence:'Unavailable',factors:[]};
  const sleep=sleepScore(day,goal), factors=[];
  if(sleep.value!=null) factors.push({label:'Last night’s sleep',value:sleep.value,weight:0.6,detail:`Sleep score ${sleep.value}/100`});
  const hrvBase=baseline(days,day.date,'hrv'),rhrBase=baseline(days,day.date,'restingHeartRate');
  if(day.hrv!=null && hrvBase) factors.push({label:'Heart rate variability',value:clamp(70+(day.hrv/hrvBase-1)*150),weight:0.25,detail:`${day.hrv} ms · personal baseline ${round(hrvBase)} ms`});
  if(day.restingHeartRate!=null && rhrBase) factors.push({label:'Resting heart rate',value:clamp(75+(rhrBase/day.restingHeartRate-1)*200),weight:0.15,detail:`${day.restingHeartRate} bpm · personal baseline ${round(rhrBase)} bpm`});
  // Sleep plus at least one personal-baseline vital is required; sleep alone is not recovery.
  if(factors.length<2) return {value:null,confidence:'Needs baseline',factors};
  const sum=factors.reduce((s,f)=>s+f.weight,0);
  return {value:round(factors.reduce((s,f)=>s+f.value*f.weight,0)/sum),confidence:days.filter(d=>d.date<day.date).length>=14 && factors.length===3?'Moderate':'Limited',factors};
}
export function bodyEnergy(day,days,goal=480) {
  const recovery=recoveryScore(day,days,goal);
  if(recovery.value==null || !day?.energySamples?.length) return {value:null,confidence:'Needs activity data',points:[],factors:[]};
  let value=recovery.value; const points=[{hour:day.energySamples[0].hour-1,value:round(value)}];
  for(const sample of day.energySamples) {
    const activity=(sample.activeMinutes||0)*0.16+(sample.steps||0)*0.0015;
    const elevated=sample.heartRate!=null && day.restingHeartRate!=null? Math.max(0,sample.heartRate-day.restingHeartRate)*0.035:0;
    const rest=(sample.restMinutes||0)*0.025;
    value=clamp(value-activity-elevated+rest);
    points.push({hour:sample.hour,value:round(value)});
  }
  return {value:round(value),confidence:'Experimental',points,factors:[{label:'Starting energy',value:recovery.value,weight:1,detail:'Initialized from today’s recovery estimate'},{label:'Movement & heart load',value:round(recovery.value-value),weight:1,detail:'Activity minutes, steps and heart rate use energy; recorded rest replenishes it'}]};
}
export function demoData() {
  const today=localDate(), seed=[425,438,456,412,470,460,442,481,450,435,464,470,455,468];
  const days=Array.from({length:28},(_,i) => {
    const d=new Date(today+'T12:00:00+05:30');d.setUTCDate(d.getUTCDate()-(27-i));
    return {date:d.toISOString().slice(0,10),sleepMinutes:seed[i%14],sleepEfficiency:86+i%9,sleepRegularity:70+i%17,restingHeartRate:59+i%5,hrv:55+i%11,spo2:96+i%3,respiratoryRate:14.1+(i%5)*0.2,skinTemperature:33.8+(i%4)*0.1,steps:6200+(i*743)%4900,activeMinutes:32+i%30,activeCalories:240+i*7%190,distanceKm:4.2+i%4*0.6,floors:4+i%8};
  });
  Object.assign(days.at(-1),{sleepMinutes:468,sleepEfficiency:90,sleepRegularity:75,restingHeartRate:58,hrv:64,spo2:98,respiratoryRate:14.6,skinTemperature:34.1,steps:6842,activeMinutes:42,activeCalories:326,distanceKm:4.8,floors:8,weightKg:72.4,bodyFat:19.2,samsungEnergy:86,sleepStages:[{stage:'Light',minutes:42},{stage:'Deep',minutes:36},{stage:'Light',minutes:25},{stage:'REM',minutes:24},{stage:'Awake',minutes:9},{stage:'Light',minutes:53},{stage:'Deep',minutes:45},{stage:'Light',minutes:36},{stage:'REM',minutes:31},{stage:'Light',minutes:58},{stage:'Awake',minutes:12},{stage:'REM',minutes:36},{stage:'Light',minutes:47},{stage:'REM',minutes:35}],energySamples:[{hour:7,steps:200,activeMinutes:0,restMinutes:30,heartRate:62},{hour:8,steps:1900,activeMinutes:18,heartRate:105},{hour:9,steps:600,activeMinutes:4,restMinutes:20,heartRate:75},{hour:10,steps:400,activeMinutes:2,restMinutes:28,heartRate:69},{hour:11,steps:1000,activeMinutes:8,heartRate:83},{hour:12,steps:1200,activeMinutes:6,heartRate:78},{hour:13,steps:1542,activeMinutes:4,restMinutes:15,heartRate:72}]});
  return {version:1,source:'Demo · Galaxy Watch6 Classic',days};
}
export const METRICS=[
  {key:'restingHeartRate',label:'Resting heart rate',unit:'bpm',icon:'heart',color:'#bd6b5c',description:'Your heart rate at rest. Compare it with your own baseline over time.'},
  {key:'hrv',label:'Heart rate variability',unit:'ms',icon:'pulse',color:'#679a80',description:'Variation between heartbeats. Availability depends on the data source; Samsung may not share this through Health Connect.'},
  {key:'spo2',label:'Blood oxygen',unit:'%',icon:'droplet',color:'#668cb1',description:'Recorded oxygen saturation. Watch readings can be affected by fit and movement.'},
  {key:'respiratoryRate',label:'Respiratory rate',unit:'/min',icon:'wind',color:'#a090b6',description:'Recorded breaths per minute. Not every provider shares this measurement.'},
  {key:'skinTemperature',label:'Skin temperature',unit:'°C',icon:'thermometer',color:'#c39b61',description:'Skin temperature from a wearable is different from core body temperature.'}
];
